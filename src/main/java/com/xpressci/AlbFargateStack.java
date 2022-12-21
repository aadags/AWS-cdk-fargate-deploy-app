package com.xpressci;

import com.sun.tools.javac.util.List;
import software.amazon.awscdk.*;
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
import software.constructs.Construct;

public class AlbFargateStack extends Stack {
    public AlbFargateStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AlbFargateStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        IVpc vpc;

        if(System.getProperty("VPC") == null) {
            vpc = new Vpc(this, "MyVpc", VpcProps.builder().maxAzs(2).build());
        } else {
            vpc = Vpc.fromLookup(this, "VpcLookup", VpcLookupOptions.builder()
                    .vpcId(System.getProperty("VPC"))
                    .build());
        }

        // Create the ECS Service
        Cluster cluster = new Cluster(this, "FargateCluster", ClusterProps.builder().vpc(vpc)
                .clusterName(System.getProperty("FARGATE_CLUSTER", "UAT")).build());

        // Create a load-balanced Fargate service and make it public
        ApplicationLoadBalancedFargateService loadBalancedFargateService = ApplicationLoadBalancedFargateService.Builder.create(this, "MyFargateService")
                .cluster(cluster)           // Required
                .cpu(1024)                   // Default is 256
                .desiredCount(Integer.valueOf(System.getProperty("FARGATE_SCALE", "1")))            // Default is 1
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(ContainerImage.fromEcrRepository(Repository.fromRepositoryName(this, "MyRepo", System.getProperty("FARGATE_REPO", "test"))))
                                .containerName("web")
                                .enableLogging(true)
                                .containerPort(8080)
                                .build())
                .memoryLimitMiB(2048)       // Default is 512
                .publicLoadBalancer(true)   // Default is true
                .assignPublicIp(true)
                .build();

        loadBalancedFargateService.getTargetGroup().configureHealthCheck(
                HealthCheck.builder().path("/actuator/health").healthyThresholdCount(2).unhealthyThresholdCount(5).build()
        );


    }
}
