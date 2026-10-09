package cz.upce.fei.ems.backend.controller;

import cz.upce.fei.ems.backend.dto.AuthResponseDto;
import cz.upce.fei.ems.backend.dto.LoginRequestDto;
import cz.upce.fei.ems.backend.dto.RegisterRequestDto;
import cz.upce.fei.ems.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "User Registration",
            description = "Registers a new user and stores their public E2EE key.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Registration successful", content = @Content(schema = @Schema(implementation = AuthResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request or weak password", content = @Content),
                    @ApiResponse(responseCode = "409", description = "Username already taken", content = @Content)
            }
    )
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(
            summary = "User Login",
            description = "Returns JWT token.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful.", content = @Content(schema = @Schema(implementation = AuthResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid username or password", content = @Content)
            }
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }

    @Operation(
            summary = "Get User Public Key",
            description = "Returns the public E2EE key for a given user so the sender can encrypt a message for them."
    )
    @GetMapping("/public-key/{username}")
    public ResponseEntity<String> getPublicKey(@PathVariable String username) {
        return ResponseEntity.ok(authService.getPublicKey(username));
    }
}