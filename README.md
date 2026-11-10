# FutApp

Organização dos jogos de futebol de um grupo de amigos: convocatória, equipas, registo do jogo, votação do MVP e estatísticas.

## Correr localmente

Requisitos: Java 17 e Maven 3.9.

```
mvn clean verify        # compila e corre os testes
mvn spring-boot:run     # arranca em http://localhost:8080
```

No arranque é criada a conta de administrador, se ainda não existir: em desenvolvimento, `admin` / `admin123`.
Os restantes membros registam-se com um link de convite gerado pelo Admin.

## Produção

Arrancar com o perfil `prod` e as variáveis de ambiente:
`SPRING_PROFILES_ACTIVE=prod`, `FUTAPP_ADMIN_PASSWORD` (e opcionalmente `FUTAPP_ADMIN_USERNAME`), `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `FUTAPP_REMEMBER_KEY`, `FUTAPP_CONVITE_KEY`.

As duas chaves devem ser textos longos e aleatórios. Mudar a `FUTAPP_CONVITE_KEY` invalida todos os links de convite já enviados.
