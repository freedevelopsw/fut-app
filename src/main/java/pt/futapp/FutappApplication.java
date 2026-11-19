package pt.futapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import pt.futapp.config.UrlBaseDados;

@SpringBootApplication
public class FutappApplication {

    public static void main(String[] args) {
        // O Liquibase 5 envia estatísticas de uso para config.liquibase.com no arranque: desligado
        // (a app não comunica com terceiros e o arranque no Cloud Run não fica à espera disso).
        if (System.getProperty("liquibase.analytics.enabled") == null) {
            System.setProperty("liquibase.analytics.enabled", "false");
        }
        UrlBaseDados.aplicar();
        SpringApplication.run(FutappApplication.class, args);
    }
}
