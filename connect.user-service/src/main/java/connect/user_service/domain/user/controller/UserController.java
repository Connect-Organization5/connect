package connect.user_service.domain.user.controller;

import connect.user_service.domain.user.dto.request.UpdateUser;
import connect.user_service.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUser(@PathVariable String userId) {

        return null;
    }

    @GetMapping
    public ResponseEntity<?> getAllUsers(@RequestParam int page, @RequestParam int size) {

        return null;
    }

    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUser(@RequestBody UpdateUser updateUser) {

        return null;
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId) {

        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
