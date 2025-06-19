package helloworld.example.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

public class MainVerticle extends AbstractVerticle {

  private final List<JsonObject> mensajes = new ArrayList<>();

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    Router router = Router.router(vertx);

    //Endpoint para el POST -> Recibe los datos y los guarda
    router.post("/hello").handler(this::handlePost);

    //Endpoint para el GET -> Muestra los datos guardados
    router.get("/hello").handler(this::handleGet);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888, http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("Servidor corriendo en http://localhost:8888/hello");
      } else {
        startPromise.fail(http.cause());
      }
    });
  }

  //Maneja POST
  private void handlePost(RoutingContext ctx) {
    ctx.request().bodyHandler(body -> {
      JsonObject jsonBody =  body.toJsonObject(); //Convierte el JSON recibido

      mensajes.add(jsonBody); //Guarda el mensaje en la lista
      System.out.println("JSON recibido: " + jsonBody.encodePrettily());

      //Responde con un JSON de confirmación
      ctx.response()
        .putHeader("content-type", "application/json")
        .end(new JsonObject()
          .put("status", "recibido")
          .put("data", jsonBody)
          .encode());
    });
  }

  private void handleGet(RoutingContext ctx) {
    ctx.response()
      .putHeader("content-type", "application/json")
      .end(new JsonObject()
        .put("mensajes_recibidos", mensajes)
        .encode());
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }
}
