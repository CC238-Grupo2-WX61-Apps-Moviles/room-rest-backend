package com.upc.backend.akira.security.service.impl;

import com.upc.backend.akira.ecommerce.domain.model.entity.User;
import com.upc.backend.akira.ecommerce.domain.model.enums.ERole;
import com.upc.backend.akira.ecommerce.domain.repository.RoleRepository;
import com.upc.backend.akira.ecommerce.domain.repository.UserRepository;
import com.upc.backend.akira.security.jwt.provider.JwtTokenProvider;
import com.upc.backend.akira.security.model.dto.request.LoginRequestDto;
import com.upc.backend.akira.security.model.dto.request.RegisterRequestDto;
import com.upc.backend.akira.security.model.dto.request.UpdatePasswordRequestDto;
import com.upc.backend.akira.security.model.dto.response.RegisteredUserResponseDto;
import com.upc.backend.akira.security.model.dto.response.TokenResponseDto;
import com.upc.backend.akira.security.service.AuthService;
import com.upc.backend.akira.shared.dto.enums.EStatus;
import com.upc.backend.akira.shared.dto.response.ApiResponse;
import com.upc.backend.akira.shared.exception.CustomException;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ModelMapper modelMapper;

    public AuthServiceImpl(AuthenticationManager authenticationManager, UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, ModelMapper modelMapper) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.modelMapper = modelMapper;
    }

    @Override
    public ApiResponse<RegisteredUserResponseDto> registerUser(RegisterRequestDto request) {
        //si el email ya está registrado
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(HttpStatus.CONFLICT, "El email '" + request.getEmail() + "' ya está registrado");
        }

        //si no existe, lo registra
        var user = User.builder()
                .name(request.getName())
                .surname(request.getSurname())
                .email(request.getEmail())
                .payment(request.getPayment())
                .numberCellphone(request.getNumberCellphone())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        var roles = roleRepository.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new CustomException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo registrar el usuario, no se encontró el rol USER"));
        user.setRoles(Collections.singleton(roles));

        var newUser = userRepository.save(user);

        var responseData = modelMapper.map(newUser, RegisteredUserResponseDto.class);

        return new ApiResponse<>("Registro correcto", EStatus.SUCCESS, responseData);
    }

    @Override
    public ApiResponse<TokenResponseDto> login(LoginRequestDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User authenticatedUser = userRepository.findByEmail(request.getEmail());

        if (authenticatedUser == null) {
            throw new CustomException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo obtener el usuario autenticado");
        }

        Long userId = authenticatedUser.getId();

        String name = authenticatedUser.getName();
        String surname = authenticatedUser.getSurname();
        String numberCellphone = authenticatedUser.getNumberCellphone();
        String email = authenticatedUser.getEmail();
        String password = authenticatedUser.getPassword();

        String token = jwtTokenProvider.generateToken(authentication);
        var responseData = new TokenResponseDto(token, userId, name, surname, numberCellphone, email, password);

        return new ApiResponse<>("Autenticación correcta", EStatus.SUCCESS, responseData);
    }

    @Override
    public ApiResponse<Void> updatePassword(UpdatePasswordRequestDto updatePasswordRequest) {
        String userEmail = updatePasswordRequest.getEmail();
        String oldPassword = updatePasswordRequest.getOldPassword();
        String newPassword = updatePasswordRequest.getNewPassword();

        // Obtén el usuario por su correo electrónico
        User user = userRepository.findByEmail(userEmail);

        // Verifica si el usuario existe
        if (user == null) {
            throw new CustomException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        // Verifica si la contraseña actual es correcta
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }

        // Actualiza la contraseña con la nueva contraseña cifrada
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return new ApiResponse<>("Contraseña actualizada con éxito", EStatus.SUCCESS, null);
    }
}