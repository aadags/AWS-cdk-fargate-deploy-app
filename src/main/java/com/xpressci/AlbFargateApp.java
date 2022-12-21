package com.xpressci;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awsconstructs.services.albfargate.*;

import java.util.ArrayList;
import java.util.List;

public class AlbFargateApp {
    public static void main(final String[] args) {
        App app = new App();

        new AlbFargateStack(app, "AlbFargateStack", StackProps.builder()
                .env(Environment.builder()
                        .account("296191284360")
                        .region("us-east-1")
                        .build())
                .build());

//        new CodePipelineStack(app, "CodePipelineStack", StackProps.builder()
//                .env(Environment.builder()
//                        .account("296191284360")
//                        .region("us-east-1")
//                        .build())
//                .build());

        app.synth();
    }
}

