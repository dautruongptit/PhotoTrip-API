package com.travelalbum.dto.request;

public class AuthDtos {

    public record GoogleLoginRequest(String idToken) {}

    public record UserDto(String id, String name, String email, String avatarUrl) {}

    public record GoogleLoginResponse(String token, UserDto user) {}
}
