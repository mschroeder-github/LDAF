package com.r6lab.sparkjava.jwt.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.r6lab.sparkjava.jwt.AuthFilter;
import com.r6lab.sparkjava.jwt.TokenService;
import com.r6lab.sparkjava.jwt.user.Role;
import com.r6lab.sparkjava.jwt.user.User;
import com.r6lab.sparkjava.jwt.user.UserService;
import java.io.IOException;
import java.util.stream.Collectors;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;
import spark.Request;
import spark.Response;
import spark.Spark;
import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.post;

public class AuthController extends AbstractTokenController {

    private static final String ROLE_PROPERTY = "role";
    private static final String TOKEN_PREFIX = "Bearer";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String USER_NAME_PROPERTY = "userName";
    private static final String FIRST_NAME_PROPERTY = "firstName";
    private static final String LAST_NAME_PROPERTY = "lastName";
    private static final String PASSWORD_PROPERTY = "password";
    private static final String SECRET_PROPERTY = "secret";
    private static final String AUTH_ENDPOINT_PREFIX = "/auth";

    private static final String BCRYPT_SALT = "$2a$10$MOu3S18JDjMntZRv/hum0."; //BCrypt.gensalt();

    private final Gson gson;
    private final UserService userService;
    private final TokenService tokenService;

    public AuthController(Gson gson, UserService userService, TokenService tokenService) {
        super(tokenService);
        this.gson = gson;
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @Override
    public void init() {
        createAdminUser();

        // AUTH FILTER
        before(new AuthFilter(AUTH_ENDPOINT_PREFIX, tokenService));

        // REGISTRATION ENDPOINT
        post(AUTH_ENDPOINT_PREFIX + "/registration", (request, response) -> register(request, response));

        // LOGIN ENDPOINT
        post(AUTH_ENDPOINT_PREFIX + "/login", (request, response) -> login(request, response));

        // LOGOUT ENDPOINT
        post(AUTH_ENDPOINT_PREFIX + "/logout", (request, response) -> logout(request));

        // REFRESH ENDPOINT
        post(AUTH_ENDPOINT_PREFIX + "/token", (request, response) -> refresh(request, response));

        // ME ENDPOINT
        get(AUTH_ENDPOINT_PREFIX + "/me", (request, response) -> me(request, response));

        // ASSIGN ROLE_PROPERTY
        post(AUTH_ENDPOINT_PREFIX + "/roles", (request, response) -> assignRole(request));

        // REVOKE ROLE_PROPERTY
        Spark.delete(AUTH_ENDPOINT_PREFIX + "/roles", (request, response) -> revokeRole(request));

    }

    private String revokeRole(Request request) throws IOException {
        if (hasRole(request, new Role[]{Role.ADMIN})) {
            String json = request.raw().getReader().lines().collect(Collectors.joining());
            JsonObject jsonRequest = this.gson.fromJson(json, JsonObject.class);
            if (jsonRequest.has(USER_NAME_PROPERTY) && jsonRequest.has(ROLE_PROPERTY)) {
                Role role = Role.valueOf(jsonRequest.get(ROLE_PROPERTY).getAsString());
                if (role != null) {
                    User user = this.userService.get(jsonRequest.get(USER_NAME_PROPERTY).getAsString());
                    if (user != null) {
                        user.revokeRole(role);
                        this.userService.update(user);
                    }
                }
            }
        } else {
            halt(401);
        }

        return "";
    }

    private String assignRole(Request request) throws IOException {
        if (hasRole(request, new Role[]{Role.ADMIN})) {
            String json = request.raw().getReader().lines().collect(Collectors.joining());
            JsonObject jsonRequest = gson.fromJson(json, JsonObject.class);
            if (jsonRequest.has(USER_NAME_PROPERTY) && jsonRequest.has(ROLE_PROPERTY)) {
                Role role = Role.valueOf(jsonRequest.get(ROLE_PROPERTY).getAsString());
                if (role != null) {
                    User user = userService.get(jsonRequest.get(USER_NAME_PROPERTY).getAsString());
                    if (user != null) {
                        user.assignRole(role);
                        userService.update(user);
                    }
                }
            }
        } else {
            halt(401);
        }

        return "";
    }

    private Object me(Request req, Response resp) {
        User user = userService.get(getUserNameFromToken(req));
        
        JSONObject userObj = new JSONObject();
        userObj.put("firstName", user.getFirstName());
        userObj.put("lastName", user.getLastName());
        userObj.put("userName", user.getUserName());
        userObj.put("logo", user.getLogo());
        userObj.put("roles", new JSONArray(user.getRoles()));
        
        return response(req, resp,
                renderModel -> {
                    renderModel.putAll(converter.toRenderModel(userObj));
                    return "authMe.html";
                },
                json -> {
                    copy(userObj, json);
                },
                model -> {
                    //TODO rdf for user
                });
    }

    private String refresh(Request request, Response response) {
        String token = getToken(request);
        String userName = getUserNameFromToken(request);
        tokenService.revokeToken(token);
        String refreshedToken = tokenService.newToken(userService.get(userName));
        response.header(AUTHORIZATION_HEADER, TOKEN_PREFIX + " " + refreshedToken);
        //TODO cookie?
        return "";
    }

    private String logout(Request request) {
        String token = getToken(request);
        tokenService.revokeToken(token);
        return "";
    }

    private String login(Request request, Response response) throws IOException {
        String json = request.raw().getReader().lines().collect(Collectors.joining());
        JsonObject jsonRequest = gson.fromJson(json, JsonObject.class);
        if (validatePost(jsonRequest)) {
            try {
                String encryptedPassword = BCrypt.hashpw(jsonRequest.get(PASSWORD_PROPERTY).getAsString(), BCRYPT_SALT);
                User user = userService.get(jsonRequest.get(USER_NAME_PROPERTY).getAsString());
                
                if (user.getPassword().equals(encryptedPassword)) {
                    String token = tokenService.newToken(user);
                    response.header(AUTHORIZATION_HEADER, TOKEN_PREFIX + " " + token);
                    response.cookie("/", "token", token, (int) (TokenService.EXPIRATION_TIME / 1000L), false, true);
                    
                } else {
                    response.status(HttpStatus.UNAUTHORIZED_401);
                    response.type(MimeTypes.Type.APPLICATION_JSON.asString());
                    JSONObject result = new JSONObject();
                    result.put("msg", "login failed");
                    return result.toString(2);
                }
            } catch (Exception e) {
                response.status(HttpStatus.UNAUTHORIZED_401);
                response.type(MimeTypes.Type.APPLICATION_JSON.asString());
                JSONObject result = new JSONObject();
                result.put("msg", e.getMessage());
                return result.toString(2);
            }
        }
        return "";
    }

    private String register(Request request, Response response) throws IOException {
        String json = request.raw().getReader().lines().collect(Collectors.joining());
        JsonObject jsonRequest = gson.fromJson(json, JsonObject.class);
        try {
            if (validatePost(jsonRequest)) {
                userService.register(jsonRequest.get(USER_NAME_PROPERTY).getAsString(), 
                        jsonRequest.get(PASSWORD_PROPERTY).getAsString(),
                        BCrypt.hashpw(jsonRequest.get(PASSWORD_PROPERTY).getAsString(), BCRYPT_SALT),
                        jsonRequest.has(FIRST_NAME_PROPERTY) ? jsonRequest.get(FIRST_NAME_PROPERTY).getAsString() : null,
                        jsonRequest.has(LAST_NAME_PROPERTY) ? jsonRequest.get(LAST_NAME_PROPERTY).getAsString() : null,
                        jsonRequest.has(SECRET_PROPERTY) ? jsonRequest.get(SECRET_PROPERTY).getAsString() : null);
                return "";
            } else {
                response.status(400);
            }
        } catch (IllegalArgumentException e) {
            response.status(400);
            return e.getMessage();
        }
        return "";
    }
    
    private void createAdminUser() {
        if(!userService.exists("admin")) {
            userService.register("admin", userService.getSettings().getAdminPassword(), BCrypt.hashpw(userService.getSettings().getAdminPassword(), BCRYPT_SALT), userService.getSettings().getSitename(), "Admin", userService.getSettings().getRegisterSecret());
            User admin = userService.get("admin");
            admin.assignRole(Role.ADMIN);
            userService.update(admin);
        }
    }
    
    private boolean validatePost(JsonObject jsonRequest) {
        return jsonRequest != null && jsonRequest.has(USER_NAME_PROPERTY) && jsonRequest.has(PASSWORD_PROPERTY);
    }

}
