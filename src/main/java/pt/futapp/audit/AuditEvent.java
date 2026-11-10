package pt.futapp.audit;

/**
 * Evento de aplicação publicado pelos serviços sempre que acontece uma ação relevante.
 * É gravado pelo {@link AuditListener} depois de a transação ser confirmada.
 *
 * @param ator         username de quem fez a ação
 * @param acao         tipo de ação
 * @param jogoId       jogo associado, quando existe
 * @param entidadeTipo tipo do registo afetado (por exemplo INSCRICAO)
 * @param entidadeId   id do registo afetado
 * @param detalhes     descrição legível da ação
 */
public record AuditEvent(String ator, Acao acao, Long jogoId, String entidadeTipo, Long entidadeId, String detalhes) {
}
