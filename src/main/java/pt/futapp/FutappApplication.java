package pt.futapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import pt.futapp.config.UrlBaseDados;

@SpringBootApplication
public class FutappApplication {

    public static void main(String[] args) {
        UrlBaseDados.aplicar();
        SpringApplication.run(FutappApplication.class, args);
    }
}
