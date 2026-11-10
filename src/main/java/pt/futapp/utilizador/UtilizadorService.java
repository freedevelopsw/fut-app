package pt.futapp.utilizador;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.audit.Acao;
import pt.futapp.audit.AuditEvent;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.jogo.JogoService;
import pt.futapp.web.Formatos;

@Service
public class UtilizadorService implements UserDetailsService {

    /** Ator registado na auditoria para ações feitas pela própria app (por exemplo, no arranque). */
    public static final String SISTEMA = "sistema";

    private final UtilizadorRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final ConviteService convites;
    private final JogoService jogos;
    private final Formatos fmt;

    public UtilizadorService(UtilizadorRepository repo, PasswordEncoder passwordEncoder,
                             ApplicationEventPublisher eventos, Clock clock, ConviteService convites,
                             JogoService jogos, Formatos fmt) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.eventos = eventos;
        this.clock = clock;
        this.convites = convites;
        this.jogos = jogos;
        this.fmt = fmt;
    }

    // ---------------------------------------------------------------- admin inicial

    /**
     * Garante que existe pelo menos um Admin. Corre no arranque da app.
     * Se o username já existir, recebe as roles de Admin e Organizador e a password não é alterada.
     *
     * @return true se a conta foi criada agora (e a password passada foi usada)
     */
    @Transactional
    public boolean garantirAdmin(String username, String nome, String password) {
        if (repo.existsByRolesContaining(Role.ADMIN)) {
            return false;
        }
        String limpo = username.trim().toLowerCase(Locale.ROOT);
        Optional<Utilizador> existente = repo.findByUsernameIgnoreCase(limpo);
        if (existente.isPresent()) {
            Utilizador u = existente.get();
            u.getRoles().add(Role.ADMIN);
            u.getRoles().add(Role.ORGANIZADOR);
            u.desbloquear();
            eventos.publishEvent(new AuditEvent(SISTEMA, Acao.ALTERACAO_ROLE, null, "UTILIZADOR", u.getId(),
                "Sem nenhum Admin: " + u.getNome() + " passou a Admin no arranque"));
            return false;
        }
        Utilizador admin = repo.save(new Utilizador(limpo, nome.trim(), passwordEncoder.encode(password),
            EnumSet.of(Role.JOGADOR, Role.ORGANIZADOR, Role.ADMIN), LocalDateTime.now(clock)));
        eventos.publishEvent(new AuditEvent(SISTEMA, Acao.REGISTO_UTILIZADOR, null, "UTILIZADOR", admin.getId(),
            "Conta de administrador " + admin.getUsername() + " criada no arranque"));
        return true;
    }

    // ---------------------------------------------------------------- registo por convite

    /** Registo feito pelo próprio jogador. Só é possível com um link de convite válido gerado pelo Admin. */
    @Transactional
    public Utilizador registar(RegistoForm form, String convite) {
        ConviteService.Validacao validacao = convites.validar(convite);
        if (!validacao.valido()) {
            throw new ConviteInvalidoException(validacao.mensagem());
        }
        String username = form.getUsername().trim().toLowerCase(Locale.ROOT);
        if (repo.existsByUsernameIgnoreCase(username)) {
            throw new OperacaoInvalidaException("Esse nome de utilizador já existe.");
        }
        Utilizador novo = repo.save(new Utilizador(username, form.getNome().trim(),
            passwordEncoder.encode(form.getPassword()), EnumSet.of(Role.JOGADOR), LocalDateTime.now(clock)));
        eventos.publishEvent(new AuditEvent(novo.getUsername(), Acao.REGISTO_UTILIZADOR, null, "UTILIZADOR",
            novo.getId(), "Registo de " + novo.getNome() + " com convite de " + validacao.criadoPor()));
        return novo;
    }

    /** Gera um link de convite. Só o Admin o pode fazer; o convite não fica guardado. */
    public ConviteService.Convite gerarConvite(int dias, String usernameAdmin) {
        Utilizador admin = exigirAdmin(usernameAdmin);
        ConviteService.Convite convite = convites.gerar(dias, admin.getUsername());
        eventos.publishEvent(new AuditEvent(admin.getUsername(), Acao.CRIACAO_CONVITE, null, "CONVITE", null,
            "Link de convite gerado, válido " + dias + (dias == 1 ? " dia" : " dias")));
        return convite;
    }

    public int validadeMaxConviteDias() {
        return convites.getValidadeMaxDias();
    }

    // ---------------------------------------------------------------- autenticação

    /** Um utilizador bloqueado é devolvido como "disabled": o Spring Security recusa o login e a sessão persistente. */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        Utilizador u = repo.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new UsernameNotFoundException("Utilizador não encontrado: " + username));
        return User.withUsername(u.getUsername())
            .password(u.getPasswordHash())
            .disabled(u.isBloqueado())
            .authorities(u.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList())
            .build();
    }

    @Transactional(readOnly = true)
    public boolean estaBloqueado(String username) {
        return repo.findByUsernameIgnoreCase(username).map(Utilizador::isBloqueado).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<EuView> vista(String username) {
        return repo.findByUsernameIgnoreCase(username)
            .map(u -> new EuView(u.getId(), u.getNomeVisivel(), u.getUsername(), u.ehGestor(), u.temRole(Role.ADMIN)));
    }

    // ---------------------------------------------------------------- password

    /**
     * O próprio utilizador muda a password, confirmando a atual.
     * As sessões persistentes noutros dispositivos deixam de valer, porque dependem da password.
     */
    @Transactional
    public void alterarPassword(String username, String atual, String nova) {
        Utilizador u = repo.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new OperacaoInvalidaException("Utilizador desconhecido."));
        if (atual == null || !passwordEncoder.matches(atual, u.getPasswordHash())) {
            throw new PasswordErradaException();
        }
        if (nova == null || nova.length() < 6 || nova.length() > 100) {
            throw new OperacaoInvalidaException("A nova password deve ter pelo menos 6 caracteres.");
        }
        if (nova.equals(atual)) {
            throw new OperacaoInvalidaException("A nova password tem de ser diferente da atual.");
        }
        u.alterarPasswordHash(passwordEncoder.encode(nova));
        eventos.publishEvent(new AuditEvent(u.getUsername(), Acao.ALTERACAO_PASSWORD, null, "UTILIZADOR",
            u.getId(), u.getNome() + " mudou a password"));
    }

    // ---------------------------------------------------------------- gestão de membros (Admin)

    /** Membros ativos primeiro, depois os bloqueados; cada grupo por nome. */
    @Transactional(readOnly = true)
    public List<MembroView> membros(String usernameAtual) {
        return repo.findAllByOrderByNomeAsc().stream()
            .sorted(Comparator.comparing(Utilizador::isBloqueado))
            .map(u -> new MembroView(u.getId(), u.getNome(), u.getUsername(),
                u.temRole(Role.ORGANIZADOR), u.temRole(Role.ADMIN),
                u.isBloqueado(), fmt.dataHora(u.getBloqueadoEm()),
                u.getUsername().equalsIgnoreCase(usernameAtual)))
            .toList();
    }

    /**
     * Define o nível de acesso de um membro (Jogador, Organizador ou Admin).
     * O Admin muda todos os níveis; o Organizador muda entre Jogador e Organizador, mas não mexe em Admins;
     * o Jogador não muda nada. Ninguém tira a si próprio o nível de Admin.
     */
    @Transactional
    public void definirNivel(Long membroId, NivelAcesso nivel, String usernameAtor) {
        Utilizador ator = repo.findByUsernameIgnoreCase(usernameAtor)
            .orElseThrow(() -> new OperacaoInvalidaException("Utilizador desconhecido."));
        Utilizador membro = carregarMembro(membroId);
        NivelAcesso anterior = NivelAcesso.de(membro);
        if (nivel == null || !opcoesNivel(ator, membro).contains(nivel)) {
            throw new OperacaoInvalidaException(motivoSemPermissao(ator, membro));
        }
        if (nivel == anterior && membro.getRoles().equals(nivel.roles())) {
            return;
        }
        membro.getRoles().clear();
        membro.getRoles().addAll(nivel.roles());
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.ALTERACAO_ROLE, null, "UTILIZADOR",
            membro.getId(), "Nível de " + membro.getNome() + ": " + anterior.getDescricao() + " → " + nivel.getDescricao()));
    }

    /** Níveis que o ator pode escolher para o membro (vazio = não pode mudar). Usado também para o dropdown. */
    public static List<NivelAcesso> opcoesNivel(Utilizador ator, Utilizador membro) {
        if (ator.isBloqueado()) {
            return List.of();
        }
        if (ator.temRole(Role.ADMIN)) {
            return ator.getId().equals(membro.getId()) ? List.of() : List.of(NivelAcesso.values());
        }
        if (ator.temRole(Role.ORGANIZADOR)) {
            return membro.temRole(Role.ADMIN) ? List.of() : List.of(NivelAcesso.JOGADOR, NivelAcesso.ORGANIZADOR);
        }
        return List.of();
    }

    /** Explica porque é que o ator não pode mudar o nível deste membro. */
    public static String motivoSemPermissao(Utilizador ator, Utilizador membro) {
        if (ator.temRole(Role.ADMIN) && !ator.isBloqueado() && ator.getId().equals(membro.getId())) {
            return "Não podes mudar o teu próprio nível de Admin.";
        }
        if (ator.temRole(Role.ORGANIZADOR) && !ator.isBloqueado()) {
            return "Só o Admin pode dar ou retirar o nível de Admin.";
        }
        return "Só o Organizador ou o Admin podem mudar roles.";
    }

    /**
     * Bloqueia um membro que já não joga: deixa de conseguir entrar (a sessão atual termina no pedido seguinte)
     * e sai das convocatórias ainda abertas. O histórico e as estatísticas anteriores mantêm-se.
     */
    @Transactional
    public void bloquear(Long membroId, String usernameAdmin) {
        Utilizador admin = exigirAdmin(usernameAdmin);
        Utilizador membro = carregarMembro(membroId);
        if (membro.getId().equals(admin.getId())) {
            throw new OperacaoInvalidaException("Não te podes bloquear a ti próprio.");
        }
        if (membro.isBloqueado()) {
            return;
        }
        membro.bloquear(LocalDateTime.now(clock));
        int removidas = jogos.removerDeConvocatoriasAbertas(membro, admin);
        eventos.publishEvent(new AuditEvent(admin.getUsername(), Acao.BLOQUEIO_UTILIZADOR, null, "UTILIZADOR",
            membro.getId(), membro.getNome() + " bloqueado"
                + (removidas > 0 ? " e retirado de " + removidas + (removidas == 1 ? " convocatória aberta" : " convocatórias abertas") : "")));
    }

    @Transactional
    public void desbloquear(Long membroId, String usernameAdmin) {
        Utilizador admin = exigirAdmin(usernameAdmin);
        Utilizador membro = carregarMembro(membroId);
        if (!membro.isBloqueado()) {
            return;
        }
        membro.desbloquear();
        eventos.publishEvent(new AuditEvent(admin.getUsername(), Acao.DESBLOQUEIO_UTILIZADOR, null, "UTILIZADOR",
            membro.getId(), membro.getNome() + " desbloqueado"));
    }

    // ---------------------------------------------------------------- apoio

    private Utilizador carregarMembro(Long id) {
        return repo.findById(id).orElseThrow(() -> new OperacaoInvalidaException("Membro não encontrado."));
    }

    private Utilizador exigirAdmin(String username) {
        Utilizador u = repo.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new OperacaoInvalidaException("Utilizador desconhecido."));
        if (!u.temRole(Role.ADMIN) || u.isBloqueado()) {
            throw new OperacaoInvalidaException("Só o Admin pode fazer isto.");
        }
        return u;
    }
}
