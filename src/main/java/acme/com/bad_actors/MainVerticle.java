package acme.com.bad_actors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.*;
import io.vertx.core.Vertx;
import io.vertx.core.MultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.rebloom.client.Client;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.JedisPool;
import java.util.Map;
import java.util.stream.Stream;

public class MainVerticle extends AbstractVerticle {

  private final Logger LOGGER = LoggerFactory.getLogger( MainVerticle.class );


  private Client getClient() {

    // I need to create a ?Jedis? first there is no 'auth()'
    // on the bloom client


    String host = "redis-10407.c98.us-east-1-4.ec2.cloud.redislabs.com"; //System.getenv("REDIS_HOST");
    int port = 10407; //System.getenv("REDIS_PORT");
    String dbpwd = "bad-actor123"; //System.getenv("REDIS_PWD");

    LOGGER.info("REDIS host="+host+" port="+port);
    JedisShardInfo shardInfo = new JedisShardInfo(host, port);
    // Auth to db
    shardInfo.setPassword(dbpwd);
    Jedis jedis = new Jedis(shardInfo);
    jedis.auth(dbpwd);
    jedis.connect();
    LOGGER.info( "Cache Response : " + jedis.ping());

    //jedis.getResource().connect();
    //LOGGER.info("jedis auth() reply:"+reply);
    Client client = new Client(jedis);
    LOGGER.info(client);
    return client;

  }

  private String getResponseEnd(HttpServerRequest req) {
    String userAgent = req.headers().get("user-agent");
    LOGGER.info("User-Agent:"+userAgent);
    String back = "<br/><br/>check <a href='/static/index.html'>Back</a>";
    if (userAgent.contains("curl")) {
      return "";
    }
    return back;
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    LOGGER.info("start---->");



    // Get a server
    HttpServer server = vertx.createHttpServer();

    // Create a Router
    Router router = Router.router(vertx);

    // Serve some simple static files
    router.route("/static/*").handler(StaticHandler.create());
    router.route("/check").handler(ctx -> {

      String name = ctx.request().params().get("name");
      LOGGER.info("/check name="+name);
      // This handler will be called for every request
      HttpServerResponse response = ctx.response();
      response.putHeader("content-type", "text/html");
      response.setChunked(true);

      try {

        boolean exists = getClient().cfExists("BadActors",name);
        LOGGER.info("Bad Actor:"+name+" exists:"+exists);
        response.write(String.valueOf(exists));

      } catch (Exception e) {
        LOGGER.error("Error checking name:"+e);
        response.write("Error checking name:"+e);

      }

      // Write to the response and end it
      response.end( getResponseEnd(ctx.request()) );

    });
    router.route("/addbad").handler(ctx -> {

      HttpServerRequest request = ctx.request();
      String name = request.params().get("name");
      LOGGER.info("/addbad name="+name);
      // This handler will be called for every request
      HttpServerResponse response = ctx.response();
      response.putHeader("content-type", "text/html");
      response.setChunked(true);
      try {
        getClient().cfAdd("BadActors",name);
        LOGGER.info("NEW Bad Actor:"+name);
        response.write("ADDED:"+name);

      } catch (Exception e) {
        LOGGER.error("Error adding name:"+e);
        response.write("Error adding name:"+e);

      }

      // Write to the response and end it
      response.end( getResponseEnd(request) );

    });
    router.route("/remove").handler(ctx -> {

      HttpServerRequest request = ctx.request();
      String name = request.params().get("name");
      LOGGER.info("/remove name="+name);
      // This handler will be called for every request
      HttpServerResponse response = ctx.response();
      response.putHeader("content-type", "text/html");
      response.setChunked(true);
      try {
        boolean deleted = getClient().cfDel("BadActors",name);
        LOGGER.info("REMOVE Bad Actor:"+name+" deleted:"+String.valueOf(deleted));
        response.write("REMOVED:"+name+":"+String.valueOf(deleted));

      } catch (Exception e) {
        LOGGER.error("Error adding name:"+e);
        response.write("Error adding name:"+e);

      }

      // Write to the response and end it
      response.end( getResponseEnd(request) );

    });
    router.route("/count").handler(ctx -> {

      HttpServerRequest request = ctx.request();
      String name = request.params().get("name");
      LOGGER.info("/count name="+name);
      LOGGER.info("count");
      // This handler will be called for every request
      HttpServerResponse response = ctx.response();
      response.putHeader("content-type", "text/html");
      response.setChunked(true);

      try {
        long count = getClient().cfCount("BadActors",name);
        LOGGER.info("COUNT:"+name+":"+count);
        response.write(String.valueOf(count));
      } catch (Exception e) {
        LOGGER.error("Error counting badactors:"+e);
        response.write("Error counting badactors:"+e);

      }

      // Write to the response and end it
      response.end( getResponseEnd(request) );

    });
    // Deal with APIs here with more routes
    router.route().handler(ctx -> {
       // This handler will be called for every request
      HttpServerResponse response = ctx.response();
      response.putHeader("content-type", "text/plain");

      // Write to the response and end it
      response.end("Hello World from Vert.x-Web!");
    });

    // Start-it-up, and pass a handler to make sure we started
    server.requestHandler(router).listen(8080, http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("HTTP server started on port 8888");
      } else {
        startPromise.fail(http.cause());
      }
    });
  }
}
