package com.r6lab.sparkjava.jwt;

import com.r6lab.sparkjava.jwt.controller.UserController;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import spark.Spark;

public final class SparkJwtExample {

    private static final String SECRET_JWT = "secret_jwt";

    private static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor();

    private final TokenService tokenService = new TokenService(SECRET_JWT);

    public void init() {

        //new AuthController(new GsonBuilder().create(), new UserService(), tokenService).init();
        new UserController(tokenService).init();

        // PERIODIC TOKENS CLEAN UP
        EXECUTOR_SERVICE.scheduleAtFixedRate(() -> {
            System.out.println("Removing expired tokens");
            tokenService.removeExpired();
        }, 60, 60, TimeUnit.SECONDS); // every minute

        Spark.exception(Exception.class, (e, request, response) -> {
            System.err.println("Exception while processing request");
            e.printStackTrace();
        });

    }

    // BOOTSTRAP
    /*
    public static void main(String[] args) {
        String salt = BCrypt.gensalt();
        System.out.println(salt);
        
        //SparkJwtExample sparkJwtExample = new SparkJwtExample();
        //sparkJwtExample.init();
    }
    */
}
