package com.itm.space.backendresources.controller;

import com.itm.space.backendresources.BaseIntegrationTest;
import com.itm.space.backendresources.api.request.UserRequest;
import com.itm.space.backendresources.api.response.UserResponse;
import com.itm.space.backendresources.exception.BackendResourcesException;
import com.itm.space.backendresources.service.UserService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для UserController.
 * Этот класс проверяет основные функции контроллера пользователей, включая:
 * - Создание пользователей.
 * - Получение информации о пользователях по ID.
 * - Проверку авторизации через эндпоинт "hello".
 */
class UserControllerIntegrationTest extends BaseIntegrationTest {

    /**
     * Мокируем UserService, чтобы изолировать тестируемый код и проверить,
     * как контроллер взаимодействует с сервисом.
     */
    @MockBean
    private UserService userService;

    /**
     * Тесты для проверки функциональности создания пользователей.
     */
    @Nested
    class CreateTests {

        /**
         * Проверяет успешное создание пользователя, если запрос валиден и пользователь не существует.
         */
        @Test
        @WithMockUser(roles = {"MODERATOR"}) // Даем тестовому пользователю роль MODERATOR
        void shouldCreateUserWhenRequestIsValidAndUserDoesNotExist() throws Exception {
            // Данные запроса для создания пользователя
            UserRequest userRequest = new UserRequest(
                    "username_TestUser",
                    "email_test@example.com",
                    "password",
                    "firstName",
                    "lastName"
            );

            // Выполняем POST-запрос и проверяем, что он завершился успешно
            mvc.perform(requestWithContent(post("/api/users"), userRequest))
                    .andExpect(status().isOk());

            // Проверяем, что метод createUser вызван один раз
            verify(userService, times(1)).createUser(any(UserRequest.class));
        }

        /**
         * Проверяет обработку конфликта, если пользователь с таким именем уже существует.
         */
        @Test
        @WithMockUser(roles = {"MODERATOR"})
        void shouldReturnConflictWhenUserWithSameNameAlreadyExists() throws Exception {
            UserRequest userRequest = new UserRequest(
                    "username_ExistingUser",
                    "existing_user@example.com",
                    "password",
                    "firstName",
                    "lastName"
            );

            // Настраиваем мок, чтобы выбрасывал исключение при вызове createUser
            doThrow(new BackendResourcesException("User already exists", HttpStatus.CONFLICT))
                    .when(userService).createUser(any(UserRequest.class));

            // Выполняем запрос и проверяем статус ответа
            mvc.perform(requestWithContent(post("/api/users"), userRequest))
                    .andExpect(status().isConflict())
                    .andDo(print()); // Печатает подробности ответа для отладки

            // Проверяем, что createUser был вызван один раз
            verify(userService, times(1)).createUser(any(UserRequest.class));
        }

        /**
         * Проверяет, что контроллер возвращает ошибку 400 (Bad Request) при некорректных данных.
         * Проходит через раз из-за констрейнов в UserRequest
         */
        @Test
        @WithMockUser(roles = {"MODERATOR"})
        void shouldReturnBadRequestWhenRequestDataIsInvalid() throws Exception {
            // Данные запроса с ошибками валидации
            UserRequest invalidUserRequest = new UserRequest(
                    "", // Пустое имя пользователя
                    "invalid_email", // Неверный формат email
                    "123", // Слишком короткий пароль
                    "", // Пустое имя
                    "" // Пустая фамилия
            );

            // Выполняем запрос и проверяем, что возвращаются соответствующие ошибки
            mvc.perform(requestWithContent(post("/api/users"), invalidUserRequest))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.username").value("Username should not be blank"))
                    .andExpect(jsonPath("$.email").value("Email should be valid"))
                    .andExpect(jsonPath("$.password").value("Password should be greater than 4 characters long"))
                    .andExpect(jsonPath("$.firstName").value("must not be blank"))
                    .andExpect(jsonPath("$.lastName").value("must not be blank"));
        }
    }

    /**
     * Тесты для проверки получения пользователя по ID.
     */
    @Nested
    class GetUserByIdTests {

        /**
         * Проверяет успешное получение данных, если пользователь существует.
         */
        @Test
        @WithMockUser(roles = "MODERATOR")
        void shouldReturnUserResponseWhenUserExists() throws Exception {
            UUID userId = UUID.randomUUID(); // Генерируем случайный ID
            UserResponse userResponse = new UserResponse(
                    "firstName",
                    "lastName",
                    "email_test@example.com",
                    List.of("ROLE_USER"), // Список ролей
                    List.of("GROUP_A", "GROUP_B") // Список групп
            );

            // Настраиваем мок, чтобы возвращал ожидаемый ответ
            when(userService.getUserById(userId)).thenReturn(userResponse);

            // Выполняем запрос и проверяем, что данные корректно возвращаются
            mvc.perform(get("/api/users/{id}", userId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("firstName"))
                    .andExpect(jsonPath("$.email").value("email_test@example.com"));
        }

        /**
         * Проверяет, что контроллер возвращает ошибку 404, если пользователь не найден.
         */
        @Test
        @WithMockUser(roles = "MODERATOR")
        void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
            UUID userId = UUID.randomUUID();

            // Настраиваем мок, чтобы выбрасывал исключение при вызове getUserById
            when(userService.getUserById(userId))
                    .thenThrow(new BackendResourcesException("User not found", HttpStatus.NOT_FOUND));

            // Выполняем запрос и проверяем статус 404
            mvc.perform(get("/api/users/{id}", userId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        /**
         * Проверяет, что пользователь без соответствующих прав получает ошибку 403 (Forbidden).
         */
        @Test
        @WithMockUser(roles = "USER")
        void shouldReturnForbiddenWhenUserRoleIsInsufficient() throws Exception {
            UUID userId = UUID.randomUUID();

            // Выполняем запрос и проверяем, что доступ запрещен
            mvc.perform(get("/api/users/{id}", userId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        /**
         * Проверяет обработку некорректного ID (например, невалидного UUID).
         */
        @Test
        @WithMockUser(roles = "MODERATOR")
        void shouldReturnBadRequestWhenUserIdIsInvalid() throws Exception {
            String invalidUserId = "invalid-uuid";

            // Выполняем запрос с некорректным ID и проверяем статус 400
            mvc.perform(get("/api/users/{id}", invalidUserId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    /**
     * Тесты для проверки работы эндпоинта "hello".
     */
    @Nested
    class HelloTests {

        /**
         * Проверяет, что авторизованный пользователь получает свой username.
         */
        @Test
        @WithMockUser(username = "testUser_ITM", roles = "MODERATOR")
        void shouldReturnUsernameWhenAuthorizedUserCallsHelloEndpoint() throws Exception {
            // Выполняем запрос и проверяем, что возвращается имя пользователя
            mvc.perform(get("/api/users/hello")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value("testUser_ITM"));
        }

        /**
         * Проверяет, что неавторизованный пользователь получает ошибку 401 (Unauthorized).
         */
        @Test
        void shouldReturnUnauthorizedWhenUnauthenticatedUserCallsHelloEndpoint() throws Exception {
            // Выполняем запрос без авторизации и проверяем статус 401
            mvc.perform(get("/api/users/hello")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Проверяет, что пользователь без роли MODERATOR получает ошибку 403 (Forbidden).
         */
        @Test
        @WithMockUser(username = "testUser_ITM", roles = "USER")
        void shouldReturnForbiddenWhenNonModeratorUserCallsHelloEndpoint() throws Exception {
            // Выполняем запрос от имени пользователя с недостаточными правами
            mvc.perform(get("/api/users/hello")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }
}
