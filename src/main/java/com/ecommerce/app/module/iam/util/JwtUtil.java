package com.ecommerce.app.module.iam.util;

import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecommerce.app.module.iam.models.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class JwtUtil {
    private String accessSecret = "accessSecret";
    private long accessExpiry = 86400000L;
    private long refreshExpiry = 86400000L+86400000L+86400000L+86400000L+86400000L+86400000L+86400000L;
    private String refreshSecret = "refreshSecret";
    ObjectMapper mapper = new ObjectMapper();
    public  String generateAccessToken(UUID userId,String email,List<Role> roles){
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("email",email )
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpiry))
                .signWith(SignatureAlgorithm.HS256, accessSecret)
                .compact();
    }

    public String generateRefreshToken(UUID userId){
        return Jwts.builder()
        .setSubject(userId.toString())
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + refreshExpiry))
        .signWith(SignatureAlgorithm.HS256, refreshSecret)
        .compact();
    }

    public Claims validateAccessToken(String token) {
        return Jwts.parser()
                .setSigningKey(accessSecret)
                .parseClaimsJws(token)
                .getBody();
    }


    public Claims validateRefreshToken(String token) {
        return Jwts.parser()
                .setSigningKey(refreshSecret)
                .parseClaimsJws(token)
                .getBody();
    }

 
    public UUID extractUserId(String token) {
        Claims claims = validateAccessToken(token);
        return UUID.fromString(claims.getSubject());
    }
    public String extractEmail(String token ){
         Claims claims = Jwts.parser()
                            .setSigningKey(accessSecret)
                            .parseClaimsJws(token)
                            .getBody();

        return claims.get("email", String.class);
    }
    public List<Role> extractRoles(String token){
        Claims claims = Jwts.parser()
                            .setSigningKey(accessSecret)
                            .parseClaimsJws(token)
                            .getBody();
                List<Role> roles = mapper.convertValue(claims.get("roles"), mapper.getTypeFactory().constructCollectionType(List.class, Role.class) );
                return roles;

    }
 
    public boolean isTokenExpired(String token) {
        try {
            validateAccessToken(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    public String refreshAccessToken(String refreshToken, String email, List<Role> roles) {
        Claims claims = validateRefreshToken(refreshToken);
        UUID userId = UUID.fromString(claims.getSubject());
        return generateAccessToken(userId, email, roles);
    }

}
