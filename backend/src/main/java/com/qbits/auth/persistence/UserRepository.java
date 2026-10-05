package com.qbits.auth.persistence;

import com.qbits.auth.domain.User;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Stores accounts. Usernames and Google subjects are unique (enforced in the database too). */
@Repository
public class UserRepository {

  private final JdbcClient jdbc;

  public UserRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void save(User u) {
    jdbc.sql(
            """
            insert into users (id, username, password_hash, google_subject, created_at)
            values (:id, :username, :passwordHash, :googleSubject, :createdAt)
            """)
        .param("id", u.id())
        .param("username", u.username())
        .param("passwordHash", u.passwordHash())
        .param("googleSubject", u.googleSubject())
        .param("createdAt", Timestamp.from(u.createdAt()))
        .update();
  }

  public Optional<User> find(UUID id) {
    return jdbc.sql("select * from users where id = :id")
        .param("id", id)
        .query(this::map)
        .optional();
  }

  public Optional<User> findByUsername(String username) {
    return jdbc.sql("select * from users where username = :username")
        .param("username", username)
        .query(this::map)
        .optional();
  }

  public Optional<User> findByGoogleSubject(String googleSubject) {
    return jdbc.sql("select * from users where google_subject = :googleSubject")
        .param("googleSubject", googleSubject)
        .query(this::map)
        .optional();
  }

  private User map(ResultSet rs, int rowNum) throws SQLException {
    return new User(
        rs.getObject("id", UUID.class),
        rs.getString("username"),
        rs.getString("password_hash"),
        rs.getString("google_subject"),
        rs.getTimestamp("created_at").toInstant());
  }
}
