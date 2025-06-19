package org.evaluator;

import io.vertx.rxjava3.core.Vertx;

public class Main{
    public static void main(String[] args){
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new AirQualityEvaluation())
                .subscribe(
                        success -> System.out.println("AirQualityEvaluation desplegado con éxito"),
                        err -> {
                            System.err.println("Error al desplegar el verticle:");
                            err.printStackTrace();
                        }
                );
    }
}