package com.nortal.library.persistence.jpa;

import com.nortal.library.core.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBookRepository extends JpaRepository<Book, String> {
    long countByLoanedTo(String loanedTo);
}
