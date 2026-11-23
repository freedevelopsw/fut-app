package pt.futapp.comum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Uma definição da app guardada na base de dados (chave → valor), alterada pelo Admin. */
@Entity
@Table(name = "configuracao")
public class Configuracao {

    @Id
    @Column(name = "chave", length = 60)
    private String chave;

    @Column(name = "valor", length = 200)
    private String valor;

    protected Configuracao() {
    }

    public Configuracao(String chave, String valor) {
        this.chave = chave;
        this.valor = valor;
    }

    public void definir(String valor) {
        this.valor = valor;
    }

    public String getChave() { return chave; }
    public String getValor() { return valor; }
}
