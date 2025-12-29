package com.nortal.library.persistence.adapter;

import com.nortal.library.core.domain.Book;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.persistence.jpa.JpaBookRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class BookRepositoryAdapter implements BookRepository {

  private final JpaBookRepository jpaRepository;

  public BookRepositoryAdapter(JpaBookRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Optional<Book> findById(String id) {
    return jpaRepository.findById(id);
  }

  @Override
  public List<Book> findAll() {
    return jpaRepository.findAll();
  }

  @Override
  public Book save(Book book) {
    return jpaRepository.save(book);
  }

  @Override
  public void delete(Book book) {
    jpaRepository.delete(book);
  }

  @Override
  public boolean existsById(String id) {
    return jpaRepository.existsById(id);
  }

  @Override
  public long countByLoanedTo(String memberId) {
    return jpaRepository.countByLoanedTo(memberId);
  }
}
