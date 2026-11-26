package cz.upce.fei.ems.backend.dto;

/** JSON response for successful REST operations that do not return a resource representation. */
public record ActionResponseDto(boolean success) {
    public static ActionResponseDto completed() {
        return new ActionResponseDto(true);
    }
}
