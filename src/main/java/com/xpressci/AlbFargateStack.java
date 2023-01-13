package com.xpressci;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.tools.javac.util.List;
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
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.SslPolicy;
import software.constructs.Construct;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AlbFargateStack extends Stack {
    public AlbFargateStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AlbFargateStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        IVpc vpc;

        if(System.getProperty("FARGATE_VPC") == null) {
            vpc = new Vpc(this, "MyVpc", VpcProps.builder().maxAzs(2).build());
        } else {
            vpc = Vpc.fromLookup(this, "VpcLookup", VpcLookupOptions.builder()
                    .vpcId(System.getProperty("FARGATE_VPC"))
                    .build());
        }

        // Create the ECS Service
        Cluster cluster = new Cluster(this, "FargateCluster", ClusterProps.builder().vpc(vpc)
                .clusterName(System.getProperty("FARGATE_CLUSTER", "UAT")).build());

        //SSL
        ICertificate certificate = Certificate.fromCertificateArn(this, "Cert", System.getProperty("FARGATE_SSL"));

        //Environment
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> env = new HashMap<>();
        try {
            env = mapper.readValue(System.getProperty("FARGATE_ENV"), Map.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //image
        ApplicationLoadBalancedTaskImageOptions applicationLoadBalancedTaskImageOptions;
        if(System.getProperty("FARGATE_APP_PORT") != null) {
            applicationLoadBalancedTaskImageOptions = ApplicationLoadBalancedTaskImageOptions.builder()
                    .image(ContainerImage.fromEcrRepository(Repository.fromRepositoryArn(this, "MyRepo", System.getProperty("FARGATE_REPO_ARN", "test"))))
                    .containerName("web")
                    .enableLogging(true)
                    .containerPort(Integer.valueOf(System.getProperty("FARGATE_APP_PORT")))
                    .environment(env)
                    .build();
        } else {
            applicationLoadBalancedTaskImageOptions = ApplicationLoadBalancedTaskImageOptions.builder()
                    .image(ContainerImage.fromEcrRepository(Repository.fromRepositoryArn(this, "MyRepo", System.getProperty("FARGATE_REPO_ARN", "test"))))
                    .containerName("web")
                    .enableLogging(true)
                    .build();
        }

        // Create a load-balanced Fargate service and make it public
        ApplicationLoadBalancedFargateService loadBalancedFargateService = ApplicationLoadBalancedFargateService.Builder.create(this, "MyFargateService")
                .cluster(cluster)           // Required
                .cpu(Integer.valueOf(System.getProperty("FARGATE_CPU", "256")))                   // Default is 256
                .desiredCount(Integer.valueOf(System.getProperty("FARGATE_SCALE", "1")))            // Default is 1
                .taskImageOptions(applicationLoadBalancedTaskImageOptions)
                .memoryLimitMiB(Integer.valueOf(System.getProperty("FARGATE_MEMORY", "512")))       // Default is 512
                .publicLoadBalancer(true)   // Default is true
                .loadBalancerName(cluster.getClusterName())
                .serviceName(System.getProperty("FARGATE_APP_NAME"))
                .certificate(certificate)
                .sslPolicy(SslPolicy.RECOMMENDED)
                .domainName(System.getProperty("FARGATE_URL"))
                .build();

        loadBalancedFargateService.getTargetGroup().configureHealthCheck(
                HealthCheck.builder().path(System.getProperty("FARGATE_HEALTH_CHECK")).healthyThresholdCount(2).unhealthyThresholdCount(5).build()
        );

        if(System.getProperty("FARGATE_AUTO_SCALE") != "0") {
            ScalableTaskCount scalableTarget = loadBalancedFargateService.getService().autoScaleTaskCount(
                    EnableScalingProps.builder().minCapacity(1)
                            .maxCapacity(Integer.valueOf(System.getProperty("FARGATE_AUTO_SCALE"))).build()
            );

            scalableTarget.scaleOnCpuUtilization("CpuScaling", CpuUtilizationScalingProps.builder()
                    .targetUtilizationPercent(50)
                    .build());

            scalableTarget.scaleOnMemoryUtilization("MemoryScaling", MemoryUtilizationScalingProps.builder()
                    .targetUtilizationPercent(50)
                    .build());
        }

    }
}
