package org.example;

import com.fasterxml.jackson.databind.SerializationFeature;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.example.entity.AirQuality;
import redis.clients.jedis.Jedis;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class Main extends AbstractVerticle{
    private final ObjectMapper mapper = new ObjectMapper();
    private Jedis jedis;

    @Override
    public void start(){
        //Configuración de Redis y ObjectMapper
        String redisHost = System.getenv("REDIS_HOST");
        String redisPortStr = System.getenv("REDIS_PORT");

        if (redisHost == null || redisPortStr == null){
            throw new IllegalStateException("REDIS_HOST y REDIS_PORT deben estar definidas como variables de entorno");
        }

        int redisPort = Integer.parseInt(redisPortStr);
        jedis = new Jedis(redisHost, redisPort);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        //POST
        router.post("/air-quality").handler(ctx -> {
            try{
                AirQuality aq = mapper.readValue(ctx.getBodyAsString(), AirQuality.class);
                String locationId = ctx.request().getParam("locationId");

                if(locationId == null || locationId.isBlank()){
                    ctx.response().setStatusCode(400).end("locationId es requerido como query param");
                    return;
                }

                String key = "air:" + locationId + ":" + aq.getTimestamp();
                String json = mapper.writeValueAsString(aq);
                jedis.set(key, json);

                ctx.response().setStatusCode(200).end("Medicion guardada");
            } catch (Exception e){
                ctx.response().setStatusCode(500).end("Error al guardar: " + e.getMessage());
            }
        });

        //GET
        router.get("/air-quality").handler(ctx -> {
            String locationId = ctx.request().getParam("locationId");
            String time = ctx.request().getParam("time");

            if(locationId == null || time == null){
                ctx.response().setStatusCode(400).end("Los parametros locationId y time son requeridos");
                return;
            }

            String key = "air:" + locationId + ":" + time;
            String result = jedis.get(key);

            if(result != null){
                ctx.response().putHeader("Content-Type", "application/json").end(result);
            } else{
                ctx.response().setStatusCode(404).end("Medicion no encontrada");
            }
        });

        vertx.createHttpServer().requestHandler(router).listen(8888, res -> {
            if (res.succeeded()){
                System.out.println("Servidor corriendo en http://localhost:8888");
            } else {
                res.cause().printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Main());
    }
}