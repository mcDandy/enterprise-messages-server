package cz.upce.fei.ems.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("user with id:" + id + " not found");
    }

    public UserNotFoundException(String string) {
        super("user with username:" + string + " not found");
    }
}
