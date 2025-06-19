package org.evaluator;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.client.WebClient;

import org.evaluator.model.AirQualityLevel;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public class AirQualityEvaluation extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise){
        Vertx rxVertx = Vertx.newInstance(vertx);
        Router router = Router.router(rxVertx);

        router.route().handler(BodyHandler.create());

        router.get("/air-quality-evaluation").handler(ctx -> handleEvaluation(ctx, rxVertx));

        rxVertx
                .createHttpServer()
                .requestHandler(router)
                .rxListen(8888)
                .subscribe(
                        server -> {
                            System.out.println("Servidor HTTP en http://localhost:8888");
                            startPromise.complete();
                        },
                        err -> {
                            System.err.println("Error al iniciar el servidor:");
                            err.printStackTrace();
                            startPromise.fail(err);
                        }
                );
    }

    private boolean isValidTimestamp(String isoTimestamp){
        try{

            Instant requested;
            if(isoTimestamp.endsWith("Z")){
                requested = Instant.parse(isoTimestamp);
            } else {
                requested = Instant.parse(isoTimestamp + "Z");
            }

            Instant now = Instant.now();

            Duration diff = Duration.between(requested, now);

            return !requested.isAfter(now) && diff.toHours() <= 12;
        } catch (DateTimeParseException e){
            return false;
        }
    }

    private void handleEvaluation(RoutingContext ctx, Vertx vertx) {
        String locationId = ctx.request().getParam("locationId");
        String time = ctx.request().getParam("time");

        if (locationId == null || time == null){
            ctx.response().setStatusCode(400).end("Parametros 'locationId' y 'time' son obligatorios");
            return;
        }

        if(!isValidTimestamp(time)){
            ctx.response()
                    .setStatusCode(400)
                    .end("El parametro 'time' debe estar en formato ISO-8601 y dentro de las ultimas 12 horas");
            return;
        }

        String apiKey = System.getenv("API_KEY_AEMET");

        Single<JsonObject> airQualitySingle = AirQualityClient.fetchAirQuality(vertx, locationId, time);
        Single<JsonObject> aemetSingle = AirQualityClient.fetchAemetData(vertx, locationId, apiKey);

        Single.zip(airQualitySingle, aemetSingle, (airQuality, aemet) -> evaluateAirQuality(airQuality, aemet, time)).subscribe(
                result -> ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(result.encodePrettily()),
                err -> {
                    ctx.response().setStatusCode(500).end("Error al evaluar la calidad del aire");
                    err.printStackTrace();
                }
        );
    }

    private JsonObject extractRelevantObservation(JsonObject aemet, String time){
        //Normalizamos el parámetro 'time' a formato hora AEMET: fecha + hora + ":00:00+0000"
        String normalizedTime = time.substring(0, 13) + ":00:00+0000";

        //Buscamos la observación que coincida con normalizedTime
        JsonObject observation = null;
        for (int i = 0; i < aemet.getJsonArray("data").size(); i++){
            JsonObject obs = aemet.getJsonArray("data").getJsonObject(i);
            if (obs.getString("fint").equals(normalizedTime)){
                return obs;
            }
        }

        //Si no se encuentra, se usa el último elemento
        return aemet.getJsonArray("data").getJsonObject(aemet.getJsonArray("data").size() - 1);
    }

    //Metodo que aplica la lógica de evaluación
    private JsonObject evaluateAirQuality(JsonObject airQuality, JsonObject aemet, String time){
        JsonObject result = new JsonObject();

        result.put("airQuality", airQuality);

        final JsonObject observation = extractRelevantObservation(aemet, time);
        result.put("aemetObservation", observation);

        final AirQualityLevel airQualityLevel = getAirQualityLevel(airQuality, observation);

        if (airQualityLevel == AirQualityLevel.GREEN){
            return result.put("level", airQualityLevel);
        } else if (airQualityLevel == AirQualityLevel.YELLOW || airQualityLevel == AirQualityLevel.RED) {
            notifyWarning(airQualityLevel);
        }

        result.put("level", airQualityLevel);
        return result;
    }


    private AirQualityLevel getAirQualityLevel(final JsonObject airQuality, final JsonObject observation) {
        // Se implementa el algoritmo para obtener la calidad del aire
        final double iaq = airQuality.getDouble("iaq", 0.0);

        final double windSpeed = observation.getDouble("vv", 0.0);
        final double precipitation = observation.getDouble("prec", 0.0);

        AirQualityLevel level;

        //Lógica a implementar
        // 1. Se clasifica el IAQ en verde, amarillo o rojo según la tabla de bme680
        if (iaq <= 100){
            level = AirQualityLevel.GREEN;
        } else if (iaq <= 200) {
            level = AirQualityLevel.YELLOW;
        } else {
            level = AirQualityLevel.RED;
        }

        // 2. Se modula el resultado
        // Si IAQ <= 100 (verde) => Mantener verde
        // Si IAQ entre 101 y 200 (amarillo) =>
        if (level == AirQualityLevel.YELLOW){
            //Si viento > 3 m/s o lluvia > 0.5 mm => verde
            if(windSpeed > 3 || precipitation > 0.5){
                level = AirQualityLevel.GREEN;
            }
            // Si viento <= 3m/s y sin lluvia => mantener amarillo
        }
        //Si IAQ > 200 (rojo) =>
        else if (level == AirQualityLevel.RED) {
            // Si viento > 3 m/s o precipitación > 0.5 mm => amarillo
            if(windSpeed > 3 || precipitation > 0.5){
                level = AirQualityLevel.YELLOW;
            }
            //Si viento <= 3 m/s y sin lluvia => mantener rojo
        }

        return level;
    }

    private void notifyWarning(final AirQualityLevel airQualityLevel) {
        // envío de notificación por telegram
        // mensaje distinto dependiendo del nivel de alerta que sea
        String message = "";
        switch(airQualityLevel){
            case YELLOW:
                message = "Alerta amarilla: Calidad del aire moderada";
                break;
            case RED:
                message = "Alerta roja: Calidad del aire mala. Toma precauciones";
                break;
            default:
                return;
        }

        //Envío por Telegram
        sendTelegramNotification(message);

        System.out.println("Notificación enviada: " + message);
    }

    private void sendTelegramNotification(String message){
        String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        String chatId = System.getenv("TELEGRAM_CHAT_ID");

        if (botToken == null || chatId == null){
            System.err.println("No se han configurado las variables de entorno para Telegram");
            return;
        }

        String telegramApiUrl = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        JsonObject payload = new JsonObject()
                .put("chat_id", chatId)
                .put("text", message);

        Vertx rxVertx = Vertx.newInstance(vertx);
        WebClient webClient = WebClient.create(rxVertx);

        webClient.postAbs(telegramApiUrl)
                .putHeader("Content-Type", "application/json")
                .rxSendJsonObject(payload)
                .subscribe(
                        response -> System.out.println("Mensaje enviado a Telegram: " + response.bodyAsString()),
                        error -> System.err.println("Error al enviar el mensaje a Telegram: " + error.getMessage())
                );
    }
}
