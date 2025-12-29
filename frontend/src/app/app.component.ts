import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActionResult, Book, LibraryApiService, Member } from './library.service';
import { t as translate } from './i18n';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
})
export class AppComponent {
  books: Book[] = [];
  members: Member[] = [];
  selectedBookId: string | null = null;
  selectedMemberId: string | null = null;
  bookIdInput = '';
  bookTitleInput = '';
  memberIdInput = '';
  memberNameInput = '';
  bookModalOpen = false;
  bookModalMode: 'create' | 'edit' = 'create';
  memberModalOpen = false;
  memberModalMode: 'create' | 'edit' = 'create';
  lastMessage = translate('statusIdle');
  loading = false;
  apiAvailable = true;

  private readonly api = new LibraryApiService();

  constructor() {
    this.refreshAll();
  }

  t(key: string): string {
    return translate(key);
  }

  async refreshAll(): Promise<void> {
    this.loading = true;
    try {
      const [books, members] = await Promise.all([this.api.books(), this.api.members()]);
      this.apiAvailable = true;
      this.books = [...books].sort((a, b) => a.id.localeCompare(b.id));
      this.members = [...members].sort((a, b) => a.id.localeCompare(b.id));
      const bookStillExists = this.selectedBookId && this.books.some(b => b.id === this.selectedBookId);
      const memberStillExists = this.selectedMemberId && this.members.some(m => m.id === this.selectedMemberId);
      if (!bookStillExists) {
        this.selectedBookId = this.books.length ? this.books[0].id : null;
      }
      if (!memberStillExists) {
        this.selectedMemberId = this.members.length ? this.members[0].id : null;
      }
      this.syncInputsWithSelection();
      this.lastMessage = this.lastMessage || this.t('sampleData');
    } catch (e) {
      this.apiAvailable = false;
      this.lastMessage = this.t('apiOffline');
    } finally {
      this.loading = false;
    }
  }

  async borrow(): Promise<void> {
    if (!this.selectedBookId || !this.selectedMemberId) {
      return;
    }
    await this.runAction(() => this.api.borrow(this.selectedBookId!, this.selectedMemberId!));
  }

  async reserve(): Promise<void> {
    if (!this.selectedBookId || !this.selectedMemberId) {
      return;
    }
    await this.runAction(() => this.api.reserve(this.selectedBookId!, this.selectedMemberId!));
  }

  async cancelReservation(): Promise<void> {
    if (!this.selectedBookId || !this.selectedMemberId) {
      return;
    }
    await this.runAction(() =>
      this.api.cancelReservation(this.selectedBookId!, this.selectedMemberId!)
    );
  }

  async returnBook(): Promise<void> {
    // Check only for bookId, memberId is optional for return
    if (!this.selectedBookId || !this.selectedMemberId) {
      return;
    }
    // Send the memberId to the API
    await this.runAction(() => this.api.returnBook(this.selectedBookId!, this.selectedMemberId!));
  }

  async createBook(): Promise<void> {
    if (!this.bookIdInput || !this.bookTitleInput) {
      this.lastMessage = this.t('INVALID_REQUEST');
      return;
    }
    await this.runAction(() => this.api.createBook(this.bookIdInput, this.bookTitleInput));
    this.clearBookModal();
  }

  async updateBook(): Promise<void> {
    if (!this.bookIdInput || !this.bookTitleInput) {
      this.lastMessage = this.t('INVALID_REQUEST');
      return;
    }
    await this.runAction(() => this.api.updateBook(this.bookIdInput, this.bookTitleInput));
    this.clearBookModal();
  }

  async deleteBook(id: string): Promise<void> {
    if (!id) {
      this.lastMessage = this.t('INVALID_REQUEST');
      return;
    }
    await this.runAction(() => this.api.deleteBook(id));
  }

  async createMember(): Promise<void> {
    if (!this.memberIdInput || !this.memberNameInput) {
      this.lastMessage = this.t('INVALID_REQUEST');
      return;
    }
    await this.runAction(() => this.api.createMember(this.memberIdInput, this.memberNameInput));
    this.clearMemberModal();
  }

  async updateMember(): Promise<void> {
    if (!this.memberIdInput || !this.memberNameInput) {
      this.lastMessage = this.t('INVALID_REQUEST');
      return;
    }
    await this.runAction(() => this.api.updateMember(this.memberIdInput, this.memberNameInput));
    this.clearMemberModal();
  }

  async deleteMember(id: string): Promise<void> {
    if (!id) {
      this.lastMessage = this.t('INVALID_REQUEST');
      return;
    }
    await this.runAction(() => this.api.deleteMember(id));
  }

  private async runAction(fn: () => Promise<ActionResult>): Promise<void> {
    this.loading = true;
    try {
      const result = await fn();
      this.lastMessage = this.describeResult(result);
      await this.refreshAll();
    } catch (e) {
      this.lastMessage = this.t('apiError');
    } finally {
      this.loading = false;
    }
  }

  private describeResult(result: ActionResult): string {
    if (result.ok) {
      if (typeof result.nextMemberId !== 'undefined') {
        return result.nextMemberId ? `${this.t('ok')} -> ${result.nextMemberId}` : this.t('ok');
      }
      return this.t('ok');
    }
    const reasonKey = result.reason ?? 'INVALID_REQUEST';
    return this.t(reasonKey);
  }

  get activeBook(): Book | undefined {
    return this.selectedBookId ? this.books.find(b => b.id === this.selectedBookId) : undefined;
  }

  get activeMember(): Member | undefined {
    return this.selectedMemberId ? this.members.find(m => m.id === this.selectedMemberId) : undefined;
  }

  get booksOnLoanCount(): number {
    return this.books.filter(b => !!b.loanedTo).length;
  }

  get queuedCount(): number {
    return this.books.reduce((acc, book) => acc + book.reservationQueue.length, 0);
  }

  bookStatusLabel(book: Book): string {
    if (book.loanedTo) {
      return `${this.t('loanedChip')}: ${book.loanedTo}`;
    }
    if (book.reservationQueue.length) {
      return `${this.t('queueChip')}: ${book.reservationQueue.length}`;
    }
    return this.t('availableChip');
  }

  onBookSelectionChange(id: string | null) {
    this.selectedBookId = id;
    this.syncInputsWithSelection();
  }

  selectBook(id: string) {
    this.onBookSelectionChange(id);
  }

  onMemberSelectionChange(id: string | null) {
    this.selectedMemberId = id;
    this.syncInputsWithSelection();
  }

  selectMember(id: string) {
    this.onMemberSelectionChange(id);
  }

  private syncInputsWithSelection() {
    this.bookTitleInput = this.activeBook?.title ?? '';
    this.memberNameInput = this.activeMember?.name ?? '';
  }

  openBookModal(mode: 'create' | 'edit', book?: Book) {
    this.bookModalMode = mode;
    this.bookModalOpen = true;
    if (mode === 'edit' && book) {
      this.bookIdInput = book.id;
      this.bookTitleInput = book.title;
    } else {
      this.bookIdInput = '';
      this.bookTitleInput = '';
    }
  }

  openMemberModal(mode: 'create' | 'edit', member?: Member) {
    this.memberModalMode = mode;
    this.memberModalOpen = true;
    if (mode === 'edit' && member) {
      this.memberIdInput = member.id;
      this.memberNameInput = member.name;
    } else {
      this.memberIdInput = '';
      this.memberNameInput = '';
    }
  }

  closeBookModal() {
    this.bookModalOpen = false;
    this.clearBookModal();
  }

  closeMemberModal() {
    this.memberModalOpen = false;
    this.clearMemberModal();
  }

  async submitBookModal() {
    if (this.bookModalMode === 'edit') {
      await this.updateBook();
    } else {
      await this.createBook();
    }
  }

  async submitMemberModal() {
    if (this.memberModalMode === 'edit') {
      await this.updateMember();
    } else {
      await this.createMember();
    }
  }

  private clearBookModal() {
    this.bookIdInput = '';
    this.bookTitleInput = '';
  }

  private clearMemberModal() {
    this.memberIdInput = '';
    this.memberNameInput = '';
  }
}

