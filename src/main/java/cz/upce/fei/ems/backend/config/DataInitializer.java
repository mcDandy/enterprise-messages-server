package cz.upce.fei.ems.backend.config;

//import cz.upce.fei.ems.backend.repository.UserRepository;
//import jakarta.transaction.Transactional;
//import org.springframework.security.crypto.password.PasswordEncoder;

/*
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // todo example of seed
    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = createUser("admin");
            System.out.println("First run. Admin created, username is admin password is admin.");
        }
    }

    private User createUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setHashedPassword(passwordEncoder.encode("admin"));
        return userRepository.save(u);
    }
}*/