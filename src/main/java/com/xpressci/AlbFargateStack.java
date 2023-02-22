package com.xpressci;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.ICertificate;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.IStage;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.StageOptions;
import software.amazon.awscdk.services.codepipeline.actions.EcrSourceAction;
import software.amazon.awscdk.services.codepipeline.actions.EcsDeployAction;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.route53.*;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.constructs.Construct;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlbFargateStack extends Stack {
    public AlbFargateStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AlbFargateStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        IVpc vpc;

        if(System.getenv("FARGATE_VPC").equalsIgnoreCase("new")) {
            vpc = new Vpc(this, "MyVpc", VpcProps.builder().maxAzs(2).build());
        } else {
            vpc = Vpc.fromLookup(this, "VpcLookup", VpcLookupOptions.builder()
                    .vpcId(System.getenv("FARGATE_VPC"))
                    .build());
        }

        // Create the ECS Service
        Cluster cluster = new Cluster(this, "FargateCluster", ClusterProps.builder().vpc(vpc)
                .clusterName(System.getenv("FARGATE_CLUSTER")).build());

        //SSL
        ICertificate certificate = Certificate.fromCertificateArn(this, "Certificate", System.getenv("FARGATE_SSL"));

        //Environment
//        ObjectMapper mapper = new ObjectMapper();
//        Map<String, String> env = new HashMap<>();
//        try {
//            env = mapper.readValue(System.getenv("FARGATE_ENV"), Map.class);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        //image
        ApplicationLoadBalancedTaskImageOptions applicationLoadBalancedTaskImageOptions;
        if(System.getenv("FARGATE_APP_PORT").isEmpty()) {
            applicationLoadBalancedTaskImageOptions = ApplicationLoadBalancedTaskImageOptions.builder()
                    .image(ContainerImage.fromEcrRepository(Repository.fromRepositoryArn(this, "MyRepo", System.getenv("FARGATE_REPO_ARN"))))
                    .containerName("web")
                    .enableLogging(true)
//                    .environment(env)
                    .build();
        } else {
            applicationLoadBalancedTaskImageOptions = ApplicationLoadBalancedTaskImageOptions.builder()
                    .image(ContainerImage.fromEcrRepository(Repository.fromRepositoryArn(this, "MyRepo", System.getenv("FARGATE_REPO_ARN"))))
                    .containerName("web")
                    .containerPort(Integer.valueOf(System.getenv("FARGATE_APP_PORT")))
                    .enableLogging(true)
//                    .environment(env)
                    .build();
        }

        IHostedZone zone = HostedZone.fromLookup(this, "DomainZone", HostedZoneProviderProps.builder()
                .domainName(System.getenv("FARGATE_URL")).build());


        ApplicationLoadBalancedFargateService loadBalancedFargateService = ApplicationLoadBalancedFargateService.Builder.create(this, "Service")
                .cluster(cluster)           // Required
                .cpu(Integer.valueOf(System.getenv("FARGATE_CPU")))                   // Default is 256
                .desiredCount(Integer.valueOf(System.getenv("FARGATE_SCALE")))            // Default is 1
                .taskImageOptions(applicationLoadBalancedTaskImageOptions)
                .memoryLimitMiB(Integer.valueOf(System.getenv("FARGATE_MEMORY")))       // Default is 512
                .assignPublicIp(true)
                .minHealthyPercent(100)
                .maxHealthyPercent(200)
                .serviceName(System.getenv("FARGATE_APP_NAME"))
                .taskImageOptions(applicationLoadBalancedTaskImageOptions)
                .certificate(certificate)
                .circuitBreaker(DeploymentCircuitBreaker.builder().rollback(true).build())
                .build();

        loadBalancedFargateService.getListener().addAction("Action", AddApplicationActionProps.builder()
                .priority(10)
                .conditions(List.of(ListenerCondition.hostHeaders(List.of(System.getenv("FARGATE_URL")))))
                .action(ListenerAction.forward(List.of(loadBalancedFargateService.getTargetGroup())))
                .build());

        loadBalancedFargateService.getTargetGroup().configureHealthCheck(
                HealthCheck.builder().path(System.getenv("FARGATE_HEALTH_CHECK")).healthyThresholdCount(2).unhealthyThresholdCount(5).build()
        );

        if(!System.getenv("FARGATE_AUTO_SCALE").equalsIgnoreCase("0")) {
            ScalableTaskCount scalableTarget = loadBalancedFargateService.getService().autoScaleTaskCount(
                    EnableScalingProps.builder().minCapacity(1)
                            .maxCapacity(Integer.valueOf(System.getenv("FARGATE_AUTO_SCALE"))).build()
            );

            scalableTarget.scaleOnCpuUtilization("CpuScaling", CpuUtilizationScalingProps.builder()
                    .targetUtilizationPercent(50)
                    .build());

            scalableTarget.scaleOnMemoryUtilization("MemoryScaling", MemoryUtilizationScalingProps.builder()
                    .targetUtilizationPercent(50)
                    .build());
        }

        ARecord.Builder.create(this, "ARecord")
                .target(RecordTarget.fromAlias(new LoadBalancerTarget(loadBalancedFargateService.getLoadBalancer()))).zone(zone).build();


    }
}
