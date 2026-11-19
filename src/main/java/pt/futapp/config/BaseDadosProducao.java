package pt.futapp.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Em produção a base de dados é sempre PostgreSQL, indicada em {@code DATABASE_URL}. Se faltar (ou não for
 * PostgreSQL), a app recusa arrancar logo, com uma mensagem clara, antes de criar a ligação à base de dados.
 * No Cloud Run, a revisão nova falha e a anterior continua a servir: nunca aparece uma app sem os dados.
 */
@Component
@Profile("prod")
public class BaseDadosProducao implements BeanFactoryPostProcessor, EnvironmentAware {

    static final String MENSAGEM = "Em produção é obrigatório definir DATABASE_URL com o link do PostgreSQL "
        + "(por exemplo o do Neon: postgresql://utilizador:password@servidor/base?sslmode=require).";

    private Environment ambiente;

    @Override
    public void setEnvironment(Environment environment) {
        this.ambiente = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        verificar(ambiente.getProperty("spring.datasource.url"));
    }

    static void verificar(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException(MENSAGEM);
        }
    }
}
