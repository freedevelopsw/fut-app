package pt.futapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AvisoBaseDadosTest {

    @Test
    void avisaQuandoOH2EstaNumaPastaTemporaria() {
        assertThat(AvisoBaseDados.aviso("jdbc:h2:file:/tmp/futapp/futapp;FILE_LOCK=NO")).contains("FUTAPP_DADOS");
        assertThat(AvisoBaseDados.aviso("jdbc:h2:mem:teste")).isNotNull();
    }

    @Test
    void naoAvisaComUmVolumeOuPostgres() {
        assertThat(AvisoBaseDados.aviso("jdbc:h2:file:/dados/futapp;FILE_LOCK=NO")).isNull();
        assertThat(AvisoBaseDados.aviso("jdbc:postgresql://servidor/neondb?sslmode=require")).isNull();
    }

    @Test
    void aDescricaoNaoMostraParametros() {
        assertThat(AvisoBaseDados.descrever("jdbc:h2:file:/dados/futapp;FILE_LOCK=NO")).isEqualTo("jdbc:h2:file:/dados/futapp");
        assertThat(AvisoBaseDados.descrever("jdbc:postgresql://servidor/neondb?user=x&password=y"))
            .isEqualTo("jdbc:postgresql://servidor/neondb");
    }
}
