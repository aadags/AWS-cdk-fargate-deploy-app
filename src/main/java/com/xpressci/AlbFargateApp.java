package com.xpressci;

import com.sun.tools.javac.util.List;
import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awsconstructs.services.albfargate.*;

public class AlbFargateApp {
    public static void main(final String[] args) {
        App app = new App();

        ListenerCertificate listenerCertificate = ListenerCertificate
                .fromArn("arn:aws:acm:us-east-1:123456789012:certificate/11112222-3333-1234-1234-123456789012");

        new AlbToFargate(app, "AlbToFargatePattern", new AlbToFargateProps.Builder()
                .ecrRepositoryArn("arn:aws:ecr:us-east-1:123456789012:repository/your-ecr-repo")
                .ecrImageVersion("latest")
                .listenerProps(new BaseApplicationListenerProps.Builder()
                        .certificates(List.of(listenerCertificate))
                        .build())
                .publicApi(true)
                .build());
        app.synth();
    }
}

