package com.itm.space.backendresources.service;

import com.itm.space.backendresources.BaseIntegrationTest;
import com.itm.space.backendresources.api.request.UserRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты для UserServiceImpl.
 * Проверяет основные функции сервиса работы с пользователями:
 * - Создание пользователей.
 * - Получение информации о пользователях по ID.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserServiceImplIntegrationTest extends BaseIntegrationTest {

    private final Keycloak keycloak; // Клиент Keycloak для управления пользователями
    private String createdUserId; // Хранит ID созданного пользователя для последующего удаления

    /**
     * Конструктор для внедрения зависимости Keycloak.
     */
    @Autowired
    UserServiceImplIntegrationTest(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    // *** Тесты создания пользователей ***
    @Nested
    class UserCreationTests {

        /**
         * Проверяет успешное создание нового пользователя, если он еще не существует.
         */
        @Test
        @WithMockUser(roles = "MODERATOR")
        void createUser_WhenValidRequestProvided_ShouldSucceed() throws Exception {
            UserRequest userRequest = createValidUserRequest();

            mvc.perform(requestWithContent(post("/api/users"), userRequest))
                    .andExpect(status().isOk()); // Ожидаем успешный статус

            createdUserId = findUserIdByUsername(userRequest.getUsername()); // Сохраняем ID созданного пользователя
        }

        /**
         * Проверяет, что попытка создания пользователя с дублирующим именем вызывает ошибку 409 (Conflict).
         */
        @Test
        @WithMockUser(roles = "MODERATOR")
        void createUser_WhenDuplicateUsername_ShouldReturnConflict() throws Exception {
            UserRequest userRequest = createValidUserRequest();

            // Первый запрос успешно создает пользователя
            mvc.perform(requestWithContent(post("/api/users"), userRequest))
                    .andExpect(status().isOk());

            // Повторный запрос возвращает статус 409
            mvc.perform(requestWithContent(post("/api/users"), userRequest))
                    .andDo(print()) // Логируем ответ для отладки
                    .andExpect(status().isConflict());

            createdUserId = findUserIdByUsername(userRequest.getUsername());
        }

        /**
         * Проверяет обработку ошибок валидации для поля "username".
         */
        @Test
        @WithMockUser(roles = "MODERATOR")
        void createUser_WhenInvalidUsernameProvided_ShouldReturnBadRequest() throws Exception {
            List<String> invalidUsernames = List.of("", " ", "username_that_is_way_too_long_for_this_field");

            for (String username : invalidUsernames) {
                UserRequest userRequest = new UserRequest(
                        username, "email_@example.com", "password_", "firstName_", "lastName_"
                );

                mvc.perform(requestWithContent(post("/api/users"), userRequest))
                        .andExpect(status().isBadRequest()) // Ожидаем статус 400
                        .andDo(print());
            }
        }

        /**
         * Проверяет обработку ошибок валидации для поля "email".
         */
        @Test
        @WithMockUser(roles = "MODERATOR")
        void createUser_WhenInvalidEmailProvided_ShouldReturnBadRequest() throws Exception {
            List<String> invalidEmails = List.of("invalid-email", "", "plainaddress", "@missingusername.com", "username@.com");

            for (String email : invalidEmails) {
                UserRequest userRequest = new UserRequest(
                        "username_", email, "password_", "firstName_", "lastName_"
                );

                mvc.perform(requestWithContent(post("/api/users"), userRequest))
                        .andExpect(status().isBadRequest())
                        .andDo(print());
            }
        }

        /**
         * Проверяет обработку ошибок валидации для поля "password".
         */
        @Test
        @WithMockUser(roles = "MODERATOR")
        void createUser_WhenPasswordTooShort_ShouldReturnBadRequest() throws Exception {
            List<String> shortPasswords = List.of("pas", "12", "a", "");

            for (String password : shortPasswords) {
                UserRequest userRequest = new UserRequest(
                        "username_", "email_@example.com", password, "firstName_", "lastName_"
                );

                mvc.perform(requestWithContent(post("/api/users"), userRequest))
                        .andExpect(status().isBadRequest())
                        .andDo(print());
            }
        }
    }

    // *** Тесты получения пользователей по ID ***
    @Nested
    class UserRetrievalByIdTests {

        /**
         * Проверяет, что данные существующего пользователя корректно возвращаются.
         */
        @Test
        @WithMockUser(roles = "MODERATOR")
        void getUserById_WhenUserExists_ShouldReturnUserDetails() throws Exception {
            UUID userId = UUID.fromString("00741f96-c983-4cc8-beec-750d2320d238");

            mvc.perform(requestToJson(get("/api/users/{id}", userId)))
                    .andExpect(status().isOk());
        }

        /**
         * Проверяет, что запрос несуществующего пользователя возвращает статус 5xx.
         */
        @Test
        @WithMockUser(roles = "MODERATOR")
        void getUserById_WhenUserNotFound_ShouldReturnServerError() throws Exception {
            UUID userId = UUID.randomUUID();

            mvc.perform(requestToJson(get("/api/users/{id}", userId)))
                    .andExpect(status().is5xxServerError());
        }
    }

    // *** Очистка после каждого теста ***
    @AfterEach
    public void cleanUp() {
        if (createdUserId != null) {
            keycloak.realm("ITM").users().get(createdUserId).remove();
            createdUserId = null; // Сбрасываем ID после удаления
        }
    }

    // *** Вспомогательные методы ***

    /**
     * Создает и возвращает объект запроса с корректными данными для пользователя.
     */
    private UserRequest createValidUserRequest() {
        return new UserRequest(
                "username_", "email_@example.com", "password_", "firstName_", "lastName_"
        );
    }

    /**
     * Находит ID пользователя по его username через Keycloak.
     */
    private String findUserIdByUsername(String username) {
        return keycloak.realm("ITM")
                .users()
                .search(username)
                .get(0)
                .getId();
    }
}
