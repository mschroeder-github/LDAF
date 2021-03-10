package com.r6lab.sparkjava.jwt;

import com.r6lab.sparkjava.jwt.controller.AbstractTokenController;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.logging.Logger;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import spark.Filter;
import spark.Request;
import spark.Response;
import static spark.Spark.halt;

public class AuthFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(AuthFilter.class.getName());

    private static final String TOKEN_PREFIX = "Bearer";
    private static final String LOGIN_ENDPOINT = "/login";
    private static final String REGISTRATION_ENDPOINT = "/registration";
    private static final String HTTP_POST = "POST";
    private static final String HTTP_GET = "GET";

    private final String authEndpointPrefix;

    private TokenService tokenService;

    public static final boolean ENABLED = true;
    
    public AuthFilter(String authEndpointPrefix, TokenService tokenService) {
        this.authEndpointPrefix = authEndpointPrefix;
        this.tokenService = tokenService;
    }

    @Override
    public void handle(Request request, Response response) {
        if(!ENABLED) {
            return;
        }
        
        if (!isLoginRequest(request) && !isRegistrationRequest(request) && !isRoot(request) && !isInfoPath(request)) {
            
            String token = AbstractTokenController.getToken(request);
            
            //if token == null: returns false
            if(!tokenService.validateToken(token)) {
                String accept = request.headers(HttpHeader.ACCEPT.asString());
                if(accept != null && accept.contains("html")) {

                    /*
                    request.url();//scheme host path
                    request.pathInfo();//only path
                    request.contextPath();//null
                    request.host(); //host:port
                    request.ip(); //for localhost not useable
                    request.scheme(); //http
                    request.queryString(); //everything after ?
                    */
                    
                    String uri;
                    try {
                        uri = request.uri();
                        if(request.queryString() != null && !request.queryString().trim().isEmpty()) {
                            uri += "?" + request.queryString();
                        }
                        uri = URLEncoder.encode(uri, "UTF-8");
                    } catch (UnsupportedEncodingException ex) {
                        throw new RuntimeException(ex);
                    }
                    response.redirect("/?unauthorized&uri=" + uri);
                    
                } else {
                    halt(HttpStatus.UNAUTHORIZED_401);
                }
            }
        }
    }

    private boolean isRoot(Request request) {
        return request.uri().equals("/") && request.requestMethod().equals(HTTP_GET);
    }
    
    private boolean isInfoPath(Request request) {
        return request.uri().startsWith("/info") && request.requestMethod().equals(HTTP_GET);
    }
    
    private boolean isLoginRequest(Request request) {
        return request.uri().equals(authEndpointPrefix + LOGIN_ENDPOINT) && request.requestMethod().equals(HTTP_POST);
    }

    private boolean isRegistrationRequest(Request request) {
        return request.uri().equals(authEndpointPrefix + REGISTRATION_ENDPOINT) && request.requestMethod().equals(HTTP_POST);
    }

}
