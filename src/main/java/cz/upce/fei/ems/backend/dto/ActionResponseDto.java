package cz.upce.fei.ems.backend.dto;

/**
 * JSON response for successful REST operations that do not return a resource representation.
 *
 * @param success {@code true} when the operation completed successfully
 */
public record ActionResponseDto(boolean success) {
    /**
     * Creates the standard successful action response.
     *
     * @return a response with {@code success} set to {@code true}
     */
    public static ActionResponseDto completed() {
        return new ActionResponseDto(true);
    }
}
