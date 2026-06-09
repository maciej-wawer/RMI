# -*- coding: utf-8 -*-
"""Generuje dokumentację techniczną PDF (po polsku) dla projektu WikiRMI.
Uruchomienie:  python docs/generate_report.py   ->   docs/Sprawozdanie-WikiRMI.pdf
"""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_JUSTIFY, TA_CENTER
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, PageBreak,
                                Table, TableStyle, XPreformatted)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase.pdfmetrics import registerFontFamily
from reportlab.graphics.shapes import Drawing, Rect, String, Line, Polygon

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "Sprawozdanie-WikiRMI.pdf")
FONTS = r"C:\Windows\Fonts"

def reg(name, fname):
    p = os.path.join(FONTS, fname)
    if os.path.exists(p):
        pdfmetrics.registerFont(TTFont(name, p)); return True
    return False

reg('PL', 'arial.ttf'); reg('PL-Bold', 'arialbd.ttf')
hi = reg('PL-Italic', 'ariali.ttf'); hbi = reg('PL-BoldItalic', 'arialbi.ttf')
reg('Mono', 'consola.ttf')
registerFontFamily('PL', normal='PL', bold='PL-Bold',
                   italic='PL-Italic' if hi else 'PL', boldItalic='PL-BoldItalic' if hbi else 'PL-Bold')

ACCENT = colors.HexColor('#2D6CDF'); DARK = colors.HexColor('#1F2937')
CODEBG = colors.HexColor('#F2F5FB'); GRIDC = colors.HexColor('#C9D4E8')

S = {
 'title':   ParagraphStyle('title', fontName='PL-Bold', fontSize=26, leading=30, textColor=ACCENT),
 'sub':     ParagraphStyle('sub', fontName='PL', fontSize=13, leading=18, textColor=DARK),
 'h1':      ParagraphStyle('h1', fontName='PL-Bold', fontSize=15, leading=19, textColor=ACCENT, spaceBefore=15, spaceAfter=6),
 'h2':      ParagraphStyle('h2', fontName='PL-Bold', fontSize=12, leading=15, textColor=DARK, spaceBefore=10, spaceAfter=4),
 'body':    ParagraphStyle('body', fontName='PL', fontSize=10, leading=14.5, textColor=DARK, alignment=TA_JUSTIFY, spaceAfter=5),
 'bullet':  ParagraphStyle('bullet', fontName='PL', fontSize=10, leading=14, textColor=DARK, leftIndent=16, bulletIndent=4, spaceAfter=2),
 'code':    ParagraphStyle('code', fontName='Mono', fontSize=8.0, leading=10.4, textColor=colors.HexColor('#0B2545'),
                           backColor=CODEBG, borderPadding=6, borderColor=GRIDC, borderWidth=0.5, spaceBefore=3, spaceAfter=7),
 'cap':     ParagraphStyle('cap', fontName='PL-Italic' if hi else 'PL', fontSize=8.5, leading=11, textColor=colors.HexColor('#555'), alignment=TA_CENTER, spaceAfter=6),
 'td':      ParagraphStyle('td', fontName='PL', fontSize=8.6, leading=11, textColor=DARK),
 'tdh':     ParagraphStyle('tdh', fontName='PL-Bold', fontSize=8.6, leading=11, textColor=colors.white),
 'tdm':     ParagraphStyle('tdm', fontName='Mono', fontSize=7.6, leading=9.8, textColor=colors.HexColor('#0B2545')),
}

def esc(s): return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
def P(t): return Paragraph(t, S['body'])
def H1(t): return Paragraph(esc(t), S['h1'])
def H2(t): return Paragraph(esc(t), S['h2'])
def CODE(t): return XPreformatted(esc(t), S['code'])
def BUL(items): return [Paragraph(i, S['bullet'], bulletText='•') for i in items]
def td(t, mono=False, head=False):
    return Paragraph(esc(t), S['tdh'] if head else (S['tdm'] if mono else S['td']))

def TBL(rows, widths, header=True):
    t = Table(rows, colWidths=widths, repeatRows=1 if header else 0)
    style = [('GRID', (0,0), (-1,-1), 0.4, GRIDC), ('VALIGN', (0,0), (-1,-1), 'TOP'),
             ('LEFTPADDING',(0,0),(-1,-1),5), ('RIGHTPADDING',(0,0),(-1,-1),5),
             ('TOPPADDING',(0,0),(-1,-1),3), ('BOTTOMPADDING',(0,0),(-1,-1),3),
             ('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white, CODEBG])]
    if header: style.append(('BACKGROUND', (0,0), (-1,0), ACCENT))
    t.setStyle(TableStyle(style)); return t

# ---- RMI diagram ----
def arrow(d, x1, y1, x2, y2):
    d.add(Line(x1, y1, x2, y2, strokeColor=DARK, strokeWidth=1))
    import math
    ang = math.atan2(y2 - y1, x2 - x1); s = 6
    d.add(Polygon([x2, y2, x2 - s*math.cos(ang-0.4), y2 - s*math.sin(ang-0.4),
                   x2 - s*math.cos(ang+0.4), y2 - s*math.sin(ang+0.4)], fillColor=DARK, strokeColor=DARK))
def lbl(d, x, y, t, size=8):
    d.add(String(x, y, t, fontName='PL', fontSize=size, fillColor=DARK, textAnchor='middle'))
def box(d, x, y, w, h, title, sub):
    d.add(Rect(x, y, w, h, rx=7, ry=7, fillColor=colors.HexColor('#EAF1FF'), strokeColor=ACCENT, strokeWidth=1.3))
    d.add(String(x + w/2, y + h - 17, title, fontName='PL-Bold', fontSize=10, fillColor=DARK, textAnchor='middle'))
    yy = y + h - 31
    for line in sub:
        d.add(String(x + w/2, yy, line, fontName='PL', fontSize=7.6, fillColor=DARK, textAnchor='middle')); yy -= 11
def rmi_diagram():
    d = Drawing(470, 215)
    box(d, 8, 45, 150, 110, 'Klient (Swing/FlatLaf)', ['WikiClientController', '(jedyny punkt RMI)', 'ClientCallbackImpl', 'gui/ — okna'])
    box(d, 178, 158, 114, 46, 'RMI Registry', ['port 1099'])
    box(d, 312, 45, 150, 110, 'Serwer', ['WikiServiceImpl', 'WikiStore (rdzeń)', 'sesje + semafor', 'pula wątków (powiadomienia)'])
    d.add(Rect(330, 6, 114, 26, fillColor=colors.HexColor('#FFF4E0'), strokeColor=colors.HexColor('#C99A2E')))
    d.add(String(387, 14, 'wiki.json (zapis atomowy)', fontName='PL', fontSize=7.4, fillColor=DARK, textAnchor='middle'))
    arrow(d, 120, 156, 190, 178); lbl(d, 138, 175, 'lookup')
    arrow(d, 350, 156, 285, 178); lbl(d, 338, 175, 'rebind')
    arrow(d, 160, 118, 310, 118); lbl(d, 235, 124, 'wywołania RMI (Naming.lookup, DTO)')
    arrow(d, 310, 78, 160, 78);  lbl(d, 235, 84, 'callback: powiadomienia (serwer → klient)')
    arrow(d, 387, 44, 387, 33)
    return d

story = []

# ---- TITLE ----
story += [Spacer(1, 38*mm),
          Paragraph('WikiRMI', S['title']),
          Paragraph('Rozproszony System Wiki — dokumentacja techniczna', S['sub']),
          Spacer(1, 4*mm),
          Paragraph('Programowanie Współbieżne i Rozproszone (2026) · Temat 9 — system typu wiki', S['body']),
          Spacer(1, 8*mm)]
meta = [
    [td('Tytuł', head=True), td('Autorzy', head=True)],
    [td('WikiRMI — Rozproszony System Wiki'), td('Maciej Wawer, Krystian Wasil, Marek Więcaszek')],
    [td('Technologie', head=True), td('Repozytorium / gałąź', head=True)],
    [td('Java 17 (java.rmi, java.util.concurrent), Swing + FlatLaf'), td('github.com/maciej-wawer/RMI · refaktor-wspolbieznosc')],
]
t = Table(meta, colWidths=[85*mm, 85*mm])
t.setStyle(TableStyle([('GRID',(0,0),(-1,-1),0.4,GRIDC),('VALIGN',(0,0),(-1,-1),'MIDDLE'),
                       ('BACKGROUND',(0,0),(1,0),ACCENT),('BACKGROUND',(0,2),(1,2),ACCENT),
                       ('LEFTPADDING',(0,0),(-1,-1),6),('TOPPADDING',(0,0),(-1,-1),5),('BOTTOMPADDING',(0,0),(-1,-1),5)]))
story.append(t)
story.append(Spacer(1, 3*mm))
story.append(Paragraph('<b>Podział pracy (zadaniowy):</b> Maciej Wawer — architektura RMI i warstwa serwera; '
                       'Krystian Wasil — rdzeń współbieżności (WikiStore: blokady, monitor, semafor; pula wątków powiadomień); '
                       'Marek Więcaszek — klient Swing/FlatLaf, persystencja JSON, testy.', S['body']))
story.append(PageBreak())

# ---- 1 ----
story.append(H1('1. Cel i zakres'))
story.append(P('Aplikacja rozproszona w języku Java wykorzystująca <b>RMI</b> (zdalne wywoływanie metod) oraz '
   'mechanizmy <b>programowania równoległego</b>. Realizuje system typu wiki: administrator tworzy szkielety stron '
   'i konta, użytkownicy przeglądają, wyszukują i modyfikują strony. Rdzeniem projektu jest poprawna, '
   'drobnoziarnista i wolna od zakleszczeń współbieżność przy jednoczesnym dostępie wielu klientów.'))
story.append(H2('Założenia'))
story += BUL([
   'Komunikacja klient–serwer wyłącznie przez RMI; dane przesyłane jako DTO implementujące Serializable.',
   'Rejestr RMI uruchamiany w procesie serwera (LocateRegistry.createRegistry), usługa rejestrowana przez Naming.rebind.',
   'Jedyna zewnętrzna biblioteka to FlatLaf (wygląd UI, lib/flatlaf-3.5.4.jar); reszta bez zależności — '
   'własny kodek JSON i własny zestaw testów (bez JUnit).',
   'Trwałość w czytelnym pliku JSON; po restarcie serwera stan jest w pełni odtwarzany.',
   'Ścisły rozdział warstw: kontrakt (common) — serwer (server) — klient (client).',
])

# ---- 2 ----
story.append(H1('2. Architektura'))
story.append(P('Budowa trójwarstwowa RMI. Serwer rejestruje obiekt zdalny w rejestrze (Naming.rebind), klient pobiera '
   '„namiastkę" (stub) przez Naming.lookup i wywołuje metody jak lokalne. RMI działa też w drugą stronę — callbacki '
   'serwer→klient (powiadomienia na żywo).'))
story.append(rmi_diagram())
story.append(Paragraph('Rys. 1. Schemat blokowy: Klient – RMI Registry – Serwer.', S['cap']))
story.append(H2('Warstwy'))
story.append(TBL([
   [td('Warstwa / pakiet', head=True), td('Najważniejsze klasy i odpowiedzialność', head=True)],
   [td('common', mono=True), td('Kontrakt: WikiService (extends Remote), WikiClientCallback; DTO w jednym pliku Dto (klasy zagnieżdżone); wyjątki WikiException + PageLockedException.')],
   [td('server', mono=True), td('WikiServiceImpl (extends UnicastRemoteObject); WikiStore — RDZEŃ WSPÓŁBIEŻNOŚCI (blokady + monitor + semafor + sesje); NotificationService (pula wątków — powiadomienia); JsonPersistence; PasswordHasher; WikiServer.')],
   [td('client', mono=True), td('WikiClient (FlatLaf); WikiClientController — jedyny punkt styku z RMI; ClientCallbackImpl; gui/ (Swing); MarkdownRenderer, TextDiff.')],
], [34*mm, 136*mm]))
story.append(P('<b>Konsolidacja:</b> cały rdzeń współbieżności znajduje się w jednym pliku <font name="Mono">WikiStore.java</font>, '
   'co ułatwia analizę. Sesje i limit klientów (semafor) oraz wątek sprzątający blokady zostały wciągnięte do tej klasy.'))

# ---- 3 ----
story.append(H1('3. Specyfikacja interfejsu zdalnego (WikiService)'))
story.append(P('Każda metoda zgłasza RemoteException (błąd sieci/RMI) lub WikiException (błąd domenowy). '
   'Wszystkie poza login przyjmują token sesji.'))
story.append(TBL([
   [td('Metoda', head=True), td('Opis', head=True), td('Uprawnienia', head=True)],
   [td('login / logout', mono=True), td('Uwierzytelnienie i zakończenie sesji.'), td('wszyscy')],
   [td('createUser / deleteUser / listUsers', mono=True), td('Zarządzanie kontami; usunięcie wylogowuje i zwalnia blokady (kickUser).'), td('admin')],
   [td('createPage', mono=True), td('Utworzenie strony.'), td('zalogowani')],
   [td('deletePage / forceUnlock', mono=True), td('Usunięcie strony / wymuszone zdjęcie blokady.'), td('admin')],
   [td('listPages / searchPages / getPage / listOnlineUsers', mono=True), td('Lista, wyszukiwanie, odczyt strony, lista online.'), td('wszyscy')],
   [td('acquireEditLock / renewEditLock / releaseEditLock / savePage', mono=True), td('Pozyskanie, odnowienie, zwolnienie blokady edycji oraz zapis.'), td('wszyscy / posiadacz')],
   [td('getHistory / getRevision / restoreRevision', mono=True), td('Historia, podgląd i przywracanie rewizji.'), td('wszyscy')],
   [td('changePassword', mono=True), td('Zmiana własnego hasła.'), td('wszyscy')],
   [td('subscribe / unsubscribe', mono=True), td('Rejestracja powiadomień (callback).'), td('wszyscy')],
], [74*mm, 74*mm, 22*mm]))
story.append(P('<b>Callbacki (WikiClientCallback):</b> onPageCreated, onPageChanged, onPageDeleted, onLockChanged, '
   'onPresenceChanged — odświeżają GUI innych klientów w czasie rzeczywistym.'))

story.append(PageBreak())

# ---- 4 ----
story.append(H1('4. Model współbieżności i synchronizacji'))
story.append(P('Serwer RMI obsługuje każde wywołanie klienta w osobnym wątku, więc te same dane (np. jedna strona) są '
   'dotykane przez wiele wątków. Sekcja krytyczna to jednoczesna edycja tej samej strony — bez ochrony wystąpiłby '
   'stan wyścigu lub utrata zapisu. Zastosowano wszystkie cztery mechanizmy — trzy w pliku WikiStore '
   '(blokady, monitor, semafor), a czwarty (pula wątków) w NotificationService:'))
story.append(TBL([
   [td('Mechanizm', head=True), td('Plik : linia', head=True), td('Rola', head=True)],
   [td('BLOKADY — ReentrantReadWriteLock (per strona)', mono=True), td('Page.java:23; WikiStore.java: writeLock 350/407, readLock 301', mono=True), td('Wielu czytelników, wyłączny zapis; sekcja krytyczna „sprawdź-i-ustaw".')],
   [td('MONITORY — synchronized', mono=True), td('WikiStore.java:133,140', mono=True), td('Wspólny licznik zapisów; niepodzielna inkrementacja.')],
   [td('SEMAFORY — Semaphore', mono=True), td('WikiStore.java:51, 86, 113', mono=True), td('Limit jednoczesnych klientów (sesje).')],
   [td('WĄTKI — pula wątków (ExecutorService)', mono=True), td('NotificationService.java:21,29', mono=True), td('Powiadomienia (callbacki RMI) wysyłane poza wątkiem edycji; wolny/martwy klient nie blokuje zapisu.')],
   [td('ConcurrentHashMap + putIfAbsent', mono=True), td('WikiStore.java:43–45, 212, 272', mono=True), td('Atomowe mapy i tworzenie stron/użytkowników.')],
   [td('volatile', mono=True), td('WikiStore.java:78', mono=True), td('Bezpieczna widoczność pola sesji między wątkami.')],
], [52*mm, 64*mm, 54*mm]))
story.append(H2('Sekcja krytyczna — „sprawdź i ustaw" blokadę edycji'))
story.append(CODE(
"""public LockInfoDTO acquireEditLock(String title, String token, String userName) {   // WikiStore.java
    Page p = require(title); long now = clock.now();
    p.lock().writeLock().lock();                 // BLOKADA zapisu — wpuszcza jeden wątek
    try {
        EditLock cur = p.editLock();
        if (cur != null && !cur.isExpired(now) && !cur.heldBy(token))
            throw new PageLockedException(...);  // przegrani z N wątków
        p.setEditLock(new EditLock(token, userName, now, now + leaseMs));  // zwycięzca
        return toLockInfo(...);
    } finally { p.lock().writeLock().unlock(); }
}"""))
story.append(H2('Brak zakleszczeń, brak utraconych aktualizacji'))
story += BUL([
   'Brak deadlocka: każda operacja trzyma najwyżej JEDNĄ blokadę strony naraz i nie zagnieżdża blokad — nie powstaje cykl oczekiwań.',
   'Brak lost update: savePage działa pod wyłącznym writeLock i dodatkowo sprawdza numer wersji (kontrola optymistyczna); '
   'inkrementacja wersji i dopis do historii są niepodzielne.',
   'RMI Naming: serwer LocateRegistry.createRegistry + Naming.rebind("//host:port/WikiService") (WikiServer.java:35–36); '
   'klient Naming.lookup (WikiClientController.java:35).',
])

# ---- 5 ----
story.append(H1('5. Zarządzanie pamięcią, wydajność i odporność'))
story += BUL([
   'Przeterminowane blokady edycji są wykrywane leniwie przy acquireEditLock / renewEditLock / savePage (isExpired) — zawieszony lub zamknięty klient nigdy nie blokuje strony na stałe, bez potrzeby osobnego wątku sprzątającego.',
   'Powiadomienia (callbacki RMI) wysyłane na puli wątków (ExecutorService) — wolny/„martwy" klient nie blokuje wątku edycji; klient zgłaszający RemoteException jest usuwany.',
   'Persystencja write-through: po każdej zmianie zapis do pliku tymczasowego i atomowa podmiana (ATOMIC_MOVE) + hak zamknięcia.',
   'Usunięcie konta (kickUser) zamyka sesje (oddaje pozwolenie semafora) i zwalnia blokady usuwanego użytkownika.',
   'Nieważna sesja po stronie klienta (np. konto usunięte, restart serwera) powoduje automatyczne wylogowanie do ekranu logowania (WikiException.SESSION_INVALID + UiUtils → MainFrame.forcedLogout).',
   'Klient wykonuje wywołania RMI poza wątkiem EDT (SwingWorker) — interfejs nie zamarza.',
])

# ---- 6 ----
story.append(H1('6. Funkcjonalność'))
story += BUL([
   'Logowanie z rolami (administrator / użytkownik), limit jednoczesnych klientów (semafor).',
   'Przeglądanie i wyszukiwanie pełnotekstowe stron.',
   'Edycja z blokadą (jeden edytor naraz), licznik czasu blokady, podgląd Markdown i klikalne linki [[Strona]].',
   'Powiadomienia na żywo (callbacki RMI): panel online + aktualnie edytowane, automatyczne odświeżanie.',
   'Historia wersji: przeglądanie rewizji, porównanie różnic (diff) i przywracanie.',
   'Administracja: tworzenie/usuwanie kont i stron, wymuszone odblokowanie; zmiana hasła; wygląd FlatLaf.',
])

# ---- 7 ----
story.append(H1('7. Instrukcja uruchomienia (krok po kroku)'))
story.append(P('<b>Wymagania:</b> JDK 17 (testowano na Amazon Corretto 17). Skrypty wyszukują JDK w %USERPROFILE%\\.jdks '
   'lub w JAVA_HOME i dołączają lib/*.jar (FlatLaf) do classpath.'))
story.append(CODE(
""".\\build.ps1            # kompilacja src + test do out/ (UTF-8, FlatLaf na classpath)
.\\run-server.ps1       # serwer: rejestr RMI na porcie 1099, Naming.rebind, wczytanie/seed danych
.\\run-client.ps1       # klient (Swing/FlatLaf) — uruchom kilka instancji dla demonstracji
.\\run-tests.ps1        # 5 testów współbieżności"""))
story.append(P('Domyślne konto administratora przy pierwszym uruchomieniu: <b>admin / admin123</b>.'))

# ---- 8 ----
story.append(H1('8. Testy współbieżności i wyniki'))
story.append(P('Pięć testów stricte współbieżnościowych (każdy = jeden scenariusz, z polską narracją kroków). '
   'Wynik zbiorczy: <b>5 passed, 0 failed</b>.'))
story.append(TBL([
   [td('Test', head=True), td('Co sprawdza', head=True), td('Wynik', head=True)],
   [td('Demo1 — usuwanie edytowanej strony', mono=True), td('Konflikt usunięcia z edycją; spójność stanu.'), td('PASS — zapis odrzucony bez awarii')],
   [td('Demo2 — wymuszone odblokowanie', mono=True), td('Admin zdejmuje cudzą blokadę.'), td('PASS — inny wchodzi')],
   [td('Demo3 — dwóch naraz nie wejdzie', mono=True), td('Wyścig: 2 osoby + 10 wątków naraz.'), td('PASS — weszło 1, odrzuconych 9')],
   [td('Demo4 — usunięcie edytującego', mono=True), td('Usunięcie konta: wylogowanie + zwolnienie blokad (semafor + blokada).'), td('PASS')],
   [td('Demo5 — brak utraconych zapisów', mono=True), td('50 wątków zapisuje tę samą stronę.'), td('PASS — wersja = 50')],
], [58*mm, 78*mm, 34*mm]))
story.append(P('<b>Ograniczenia:</b> pojedynczy serwer (jeden węzeł); kodek JSON obsługuje wymagany podzbiór typów; '
   'render Markdown obejmuje wybrany podzbiór składni; maksymalna liczba klientów ograniczona semaforem (domyślnie 50, '
   'konfigurowalne w ServerConfig). Po restarcie serwera stan jest w pełni odtwarzany z pliku wiki.json; '
   'blokady edycji są ulotne i nie są utrwalane.'))

story.append(H1('9. Podsumowanie'))
story.append(P('WikiRMI to kompletny system rozproszony RMI z poprawną, drobnoziarnistą i wolną od zakleszczeń '
   'współbieżnością skupioną w jednym pliku (WikiStore) i pokazaną na czterech mechanizmach. Całość pokryta pięcioma '
   'testami współbieżności (w tym wyścigiem 10 wątków i testem braku utraconych zapisów). '
   'Kod: github.com/maciej-wawer/RMI (gałąź refaktor-wspolbieznosc).'))

def footer(canvas, doc):
    canvas.saveState(); canvas.setFont('PL', 8); canvas.setFillColor(colors.HexColor('#888'))
    canvas.drawString(20*mm, 12*mm, 'WikiRMI — dokumentacja techniczna')
    canvas.drawRightString(190*mm, 12*mm, 'str. %d' % doc.page)
    canvas.setStrokeColor(GRIDC); canvas.line(20*mm, 15*mm, 190*mm, 15*mm); canvas.restoreState()

doc = SimpleDocTemplate(OUT, pagesize=A4, leftMargin=20*mm, rightMargin=20*mm, topMargin=18*mm, bottomMargin=20*mm,
                        title='WikiRMI — dokumentacja techniczna', author='Maciej Wawer, Krystian Wasil, Marek Więcaszek')
doc.build(story, onFirstPage=footer, onLaterPages=footer)
print('WROTE', OUT)
