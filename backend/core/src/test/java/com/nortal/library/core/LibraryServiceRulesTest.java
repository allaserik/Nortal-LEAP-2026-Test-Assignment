package com.nortal.library.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nortal.library.core.domain.Book;
import com.nortal.library.core.domain.Member;
import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;

class LibraryServiceRulesTest {

    private InMemoryBookRepository books;
    private InMemoryMemberRepository members;
    private LibraryService service;

    @BeforeEach
    void setUp() {
        books = new InMemoryBookRepository();
        members = new InMemoryMemberRepository();
        service = new LibraryService(books, members);

        members.save(new Member("m1", "Kertu"));
        members.save(new Member("m2", "Rasmus"));
        members.save(new Member("m3", "Liis"));

        books.save(new Book("b1", "Clean Code"));
    }

    @Test
    void borrow_fails_whenBookAlreadyLoaned() {
        Book b1 = books.findById("b1").orElseThrow();
        b1.setLoanedTo("m1");
        b1.setDueDate(LocalDate.now().plusDays(14));
        books.save(b1);

        LibraryService.Result result = service.borrowBook("b1", "m2");

        assertFalse(result.ok(), "Borrow should fail when book is already loaned");
        Book after = books.findById("b1").orElseThrow();
        assertEquals("m1", after.getLoanedTo(), "Loan holder must remain unchanged");
    }

    @Test
    void borrow_fails_whenReservationQueueNotEmptyAndBorrowerNotHead_evenIfAvailable() {
        Book b1 = books.findById("b1").orElseThrow();
        b1.setLoanedTo(null);
        b1.setDueDate(null);
        b1.getReservationQueue().add("m2"); // head is m2
        books.save(b1);

        LibraryService.Result result = service.borrowBook("b1", "m3");

        assertFalse(result.ok(), "Borrow should fail when queue exists and borrower is not head");
        Book after = books.findById("b1").orElseThrow();
        assertNull(after.getLoanedTo(), "Book should remain available");
        assertEquals(List.of("m2"), after.getReservationQueue(), "Queue must remain unchanged");
    }

    @Test
    void borrow_succeeds_forHeadOfQueue_andRemovesThemFromQueue() {
        Book b1 = books.findById("b1").orElseThrow();
        b1.setLoanedTo(null);
        b1.setDueDate(null);
        b1.getReservationQueue().add("m2");
        b1.getReservationQueue().add("m3");
        books.save(b1);

        LibraryService.Result result = service.borrowBook("b1", "m2");

        assertTrue(result.ok(), "Borrow should succeed for head of queue");
        Book after = books.findById("b1").orElseThrow();
        assertEquals("m2", after.getLoanedTo(), "Book must be loaned to borrower");
        assertEquals(List.of("m3"), after.getReservationQueue(), "Borrower must be removed from queue");
    }

    // -----------------------
    // In-memory test doubles
    // -----------------------

    static class InMemoryBookRepository implements BookRepository {
        private final Map<String, Book> store = new LinkedHashMap<>();

        @Override
        public Optional<Book> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Book> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public Book save(Book book) {
            store.put(book.getId(), book);
            return book;
        }

        @Override
        public void delete(Book book) {
            store.remove(book.getId());
        }

        @Override
        public boolean existsById(String id) {
            return store.containsKey(id);
        }
    }

    static class InMemoryMemberRepository implements MemberRepository {
        private final Map<String, Member> store = new LinkedHashMap<>();

        @Override
        public Optional<Member> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Member> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public Member save(Member member) {
            store.put(member.getId(), member);
            return member;
        }

        @Override
        public void delete(Member member) {
            store.remove(member.getId());
        }

        @Override
        public boolean existsById(String id) {
            return store.containsKey(id);
        }
    }
}
