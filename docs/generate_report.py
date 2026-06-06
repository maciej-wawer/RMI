# -*- coding: utf-8 -*-
"""Generuje sprawozdanie PDF (po polsku) dla projektu WikiRMI.
Uruchomienie:  python docs/generate_report.py
Wynik:         docs/Sprawozdanie-WikiRMI.pdf
"""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_JUSTIFY, TA_CENTER
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, PageBreak,
                                Table, TableStyle, XPreformatted, KeepTogether)
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
                   italic='PL-Italic' if hi else 'PL',
                   boldItalic='PL-BoldItalic' if hbi else 'PL-Bold')

ACCENT = colors.HexColor('#2D6CDF')
DARK = colors.HexColor('#1F2937')
CODEBG = colors.HexColor('#F2F5FB')
GRIDC = colors.HexColor('#C9D4E8')

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
def BUL(items): return [Paragraph(esc(i), S['bullet'], bulletText='•') for i in items]
def td(t, mono=False, head=False):
    return Paragraph(esc(t), S['tdh'] if head else (S['tdm'] if mono else S['td']))

def TBL(rows, widths, header=True):
    t = Table(rows, colWidths=widths, repeatRows=1 if header else 0)
    style = [('GRID', (0,0), (-1,-1), 0.4, GRIDC),
             ('VALIGN', (0,0), (-1,-1), 'TOP'),
             ('LEFTPADDING',(0,0),(-1,-1),5), ('RIGHTPADDING',(0,0),(-1,-1),5),
             ('TOPPADDING',(0,0),(-1,-1),3), ('BOTTOMPADDING',(0,0),(-1,-1),3),
             ('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white, CODEBG])]
    if header:
        style.append(('BACKGROUND', (0,0), (-1,0), ACCENT))
    t.setStyle(TableStyle(style))
    return t

# ---------------------------------------------------------------- RMI diagram
def arrow(d, x1, y1, x2, y2):
    d.add(Line(x1, y1, x2, y2, strokeColor=DARK, strokeWidth=1))
    import math
    ang = math.atan2(y2 - y1, x2 - x1); s = 6
    d.add(Polygon([x2, y2,
                   x2 - s*math.cos(ang - 0.4), y2 - s*math.sin(ang - 0.4),
                   x2 - s*math.cos(ang + 0.4), y2 - s*math.sin(ang + 0.4)],
                  fillColor=DARK, strokeColor=DARK))

def lbl(d, x, y, t, size=8):
    d.add(String(x, y, t, fontName='PL', fontSize=size, fillColor=DARK, textAnchor='middle'))

def box(d, x, y, w, h, title, sub):
    d.add(Rect(x, y, w, h, rx=7, ry=7, fillColor=colors.HexColor('#EAF1FF'), strokeColor=ACCENT, strokeWidth=1.3))
    d.add(String(x + w/2, y + h - 17, title, fontName='PL-Bold', fontSize=10, fillColor=DARK, textAnchor='middle'))
    yy = y + h - 31
    for line in sub:
        d.add(String(x + w/2, yy, line, fontName='PL', fontSize=7.6, fillColor=DARK, textAnchor='middle'))
        yy -= 11

def rmi_diagram():
    d = Drawing(470, 215)
    box(d, 8, 45, 150, 110, 'Klient (Swing)',
        ['WikiClientController', '(jedyny punkt RMI)', 'ClientCallbackImpl', 'gui/ — okna'])
    box(d, 178, 158, 114, 46, 'RMI Registry', ['port 1099'])
    box(d, 312, 45, 150, 110, 'Serwer',
        ['WikiServiceImpl', 'WikiStore (rdzeń)', 'SessionManager', 'daemon + powiadomienia'])
    d.add(Rect(330, 6, 114, 26, fillColor=colors.HexColor('#FFF4E0'), strokeColor=colors.HexColor('#C99A2E')))
    d.add(String(387, 14, 'wiki.json (zapis atomowy)', fontName='PL', fontSize=7.4, fillColor=DARK, textAnchor='middle'))
    # arrows
    arrow(d, 120, 156, 190, 178); lbl(d, 138, 175, 'lookup')
    arrow(d, 350, 156, 285, 178); lbl(d, 338, 175, 'rebind / bind')
    arrow(d, 160, 118, 310, 118); lbl(d, 235, 124, 'wywołania RMI (DTO Serializable)')
    arrow(d, 310, 78, 160, 78);  lbl(d, 235, 84, 'callback: powiadomienia (serwer → klient)')
    arrow(d, 387, 44, 387, 33)
    return d

# ---------------------------------------------------------------- content
story = []

# ---- TITLE PAGE ----
story += [Spacer(1, 40*mm),
          Paragraph('WikiRMI', S['title']),
          Paragraph('Rozproszony System Wiki (Java&nbsp;RMI)', S['sub']),
          Spacer(1, 5*mm),
          Paragraph('Sprawozdanie z projektu zaliczeniowego', S['sub']),
          Paragraph('Programowanie Współbieżne i Rozproszone (2026)', S['body']),
          Paragraph('Temat 9 — System typu wiki (administrator tworzy szkielet stron i użytkowników; '
                    'użytkownicy modyfikują strony)', S['body']),
          Spacer(1, 10*mm)]
meta = [
    [td('Tytuł programu', head=True), td('Autorzy', head=True)],
    [td('WikiRMI — Rozproszony System Wiki'), td('Maciej Wawer, Krystian Wasil, Marek Więcaszek')],
    [td('Temat', head=True), td('Technologie', head=True)],
    [td('9 — System typu wiki'), td('Java 17 (java.rmi, java.util.concurrent), Swing')],
    [td('Repozytorium', head=True), td('Data', head=True)],
    [td('github.com/maciej-wawer/RMI'), td('6 czerwca 2026')],
    [td('Podział pracy (zadaniowy)', head=True), td('', head=True)],
]
t = Table(meta, colWidths=[85*mm, 85*mm])
t.setStyle(TableStyle([('GRID',(0,0),(-1,-1),0.4,GRIDC), ('VALIGN',(0,0),(-1,-1),'MIDDLE'),
                       ('BACKGROUND',(0,0),(0,0),ACCENT),('BACKGROUND',(1,0),(1,0),ACCENT),
                       ('BACKGROUND',(0,2),(1,2),ACCENT),('BACKGROUND',(0,4),(1,4),ACCENT),
                       ('BACKGROUND',(0,6),(1,6),ACCENT),('SPAN',(0,6),(1,6)),
                       ('LEFTPADDING',(0,0),(-1,-1),6),('TOPPADDING',(0,0),(-1,-1),5),('BOTTOMPADDING',(0,0),(-1,-1),5)]))
story.append(t)
story.append(Paragraph('<b>Maciej Wawer</b> — architektura RMI i warstwa serwera: interfejs zdalny (WikiService), '
                       'WikiServiceImpl, rejestr RMI, uwierzytelnianie i sesje (SessionManager, Semaphore).', S['body']))
story.append(Paragraph('<b>Krystian Wasil</b> — rdzeń współbieżności: WikiStore, blokady (ReentrantReadWriteLock), '
                       'dzierżawa edycji, wątek daemon (LockReaperDaemon), powiadomienia (NotificationService, ExecutorService).', S['body']))
story.append(Paragraph('<b>Marek Więcaszek</b> — klient Swing (GUI, WikiClientController, callbacki), persystencja JSON '
                       '(write-through, zapis atomowy) oraz testy współbieżności i sprawozdanie.', S['body']))
story.append(PageBreak())

# ---- 1. CEL I ZAKRES ----
story.append(H1('1. Cel i zakres projektu'))
story.append(P('Celem projektu jest zbudowanie <b>aplikacji rozproszonej</b> w języku Java z wykorzystaniem '
   '<b>zdalnego wywoływania metod (RMI)</b> oraz mechanizmów <b>programowania równoległego</b>. Aplikacja realizuje '
   'system typu wiki: <b>administrator</b> tworzy szkielety stron oraz konta użytkowników, a <b>użytkownicy</b> '
   'przeglądają, wyszukują i modyfikują strony. Kluczowym wyzwaniem technicznym — i głównym przedmiotem oceny — '
   'jest <b>poprawna, drobnoziarnista i wolna od zakleszczeń współbieżność</b> przy jednoczesnym dostępie wielu '
   'klientów do tych samych zasobów.'))
story.append(H2('Przyjęte założenia'))
story += BUL([
   'Komunikacja klient–serwer wyłącznie przez interfejs zdalny RMI; dane przesyłane jako obiekty DTO implementujące Serializable.',
   'Serwer uruchamia rejestr RMI w tym samym procesie (LocateRegistry.createRegistry), co upraszcza wdrożenie do jednej komendy.',
   'Brak zewnętrznych bibliotek — własny, lekki kodek JSON oraz własny zestaw testów (bez JUnit), zgodnie z zasadą zero zależności.',
   'Trwałość danych w czytelnym pliku JSON; po restarcie serwera stan jest w pełni odtwarzany.',
   'Ścisły rozdział warstw: kontrakt (common) — implementacja serwera (server) — prezentacja kliencka (client).',
])

# ---- 2. ARCHITEKTURA ----
story.append(H1('2. Architektura i struktura logiczna'))
story.append(P('System ma klasyczną budowę trójwarstwową RMI. Klient odnajduje zdalną usługę w rejestrze '
   '(<i>lookup</i>), a następnie wywołuje jej metody. Dodatkowo serwer wywołuje metody zwrotne (<i>callback</i>) '
   'po stronie klienta, aby na bieżąco rozsyłać powiadomienia — RMI działa więc w obu kierunkach.'))
story.append(rmi_diagram())
story.append(Paragraph('Rys. 1. Schemat blokowy komunikacji Klient–Serwer z uwzględnieniem RMI Registry.', S['cap']))
story.append(H2('Warstwy i odpowiedzialności'))
story.append(TBL([
   [td('Warstwa / pakiet', head=True), td('Odpowiedzialność', head=True)],
   [td('common', mono=True), td('Kontrakt sieciowy: interfejsy zdalne (WikiService, WikiClientCallback), DTO (Serializable), wyjątki domenowe.')],
   [td('server', mono=True), td('WikiServiceImpl (warstwa RMI) + WikiStore (cała synchronizacja) + persystencja JSON, sesje, daemon, powiadomienia.')],
   [td('client', mono=True), td('GUI Swing oraz WikiClientController — jedyna klasa po stronie klienta wywołująca RMI (rozdział prezentacji od logiki).')],
], [42*mm, 128*mm]))
story.append(P('Taki podział realizuje wymaganą separację: warstwa prezentacji (Swing) nigdy nie wywołuje RMI '
   'bezpośrednio — komunikuje się przez kontroler, który tłumaczy wyjątki na komunikaty dla użytkownika i wykonuje '
   'wywołania sieciowe poza wątkiem zdarzeń Swing (EDT).'))

# ---- 3. INTERFEJS ZDALNY ----
story.append(H1('3. Specyfikacja interfejsu zdalnego'))
story.append(P('Wszystkie metody mogą zgłosić wyjątek <b>RemoteException</b> (błąd sieci/RMI) lub <b>WikiException</b> '
   '(błąd domenowy). Każda metoda poza login przyjmuje token sesji uzyskany z logowania.'))
story.append(TBL([
   [td('Metoda (WikiService)', head=True), td('Opis', head=True), td('Uprawnienia', head=True)],
   [td('login / logout', mono=True), td('Uwierzytelnienie i zakończenie sesji.'), td('wszyscy')],
   [td('createUser / deleteUser / listUsers', mono=True), td('Zarządzanie kontami użytkowników.'), td('admin')],
   [td('createPage', mono=True), td('Utworzenie nowej strony (szkieletu).'), td('zalogowani')],
   [td('deletePage', mono=True), td('Usunięcie strony.'), td('admin')],
   [td('listPages / searchPages / getPage', mono=True), td('Lista, wyszukiwanie pełnotekstowe, odczyt strony.'), td('wszyscy')],
   [td('listOnlineUsers', mono=True), td('Lista zalogowanych użytkowników.'), td('wszyscy')],
   [td('acquireEditLock / renewEditLock / releaseEditLock', mono=True), td('Pozyskanie, odnowienie i zwolnienie blokady edycji.'), td('wszyscy')],
   [td('savePage', mono=True), td('Zapis treści (wymaga blokady i zgodnej wersji).'), td('posiadacz blokady')],
   [td('getHistory / getRevision / restoreRevision', mono=True), td('Historia wersji, podgląd i przywracanie rewizji.'), td('wszyscy')],
   [td('changePassword', mono=True), td('Zmiana własnego hasła.'), td('wszyscy')],
   [td('forceUnlock', mono=True), td('Wymuszone zdjęcie blokady ze strony.'), td('admin')],
   [td('subscribe / unsubscribe', mono=True), td('Rejestracja/wyrejestrowanie powiadomień (callback).'), td('wszyscy')],
], [70*mm, 78*mm, 22*mm]))
story.append(P('Interfejs zwrotny <b>WikiClientCallback</b> (serwer → klient): onPageCreated, onPageChanged, '
   'onPageDeleted, onLockChanged oraz onPresenceChanged — pozwala odświeżać GUI innych klientów w czasie rzeczywistym.'))

story.append(PageBreak())

# ---- 4. MODEL WSPÓŁBIEŻNOŚCI ----
story.append(H1('4. Model współbieżności i synchronizacji'))
story.append(H2('4.1. Sekcja krytyczna: jednoczesna edycja tej samej strony'))
story.append(P('Najważniejsza sekcja krytyczna to jednoczesna próba edycji tej samej strony przez wielu klientów. '
   'Bez ochrony dwóch użytkowników mogłoby równolegle zmodyfikować stronę i jeden zapis zostałby utracony '
   '(<i>lost update</i>) lub czytelnik zobaczyłby stronę w stanie niespójnym.'))
story.append(H2('4.2. Dwa współpracujące mechanizmy'))
story.append(P('Rozwiązanie celowo rozdziela <b>dwa różne mechanizmy</b>:'))
story += BUL([
   'Blokada-dzierżawa (logiczna) — stan „użytkownik X edytuje stronę”, utrzymywany przez czas namysłu (sekundy–minuty). '
   'To NIE jest monitor Javy trzymany przez całe wywołanie sieciowe, lecz zwykłe pole stanu (EditLock = posiadacz + termin wygaśnięcia). '
   'Pozyskanie odbywa się metodą „sprawdź i ustaw” (check-and-set), więc z N żądań wygrywa dokładnie jedno.',
   'ReentrantReadWriteLock na każdą stronę (pamięciowa) — trzymana tylko przez mikrosekundy odczytu/zapisu pól strony. '
   'Wielu czytelników naraz (readLock), wyłączny zapis (writeLock); żaden czytelnik nie zobaczy połowy zapisu. '
   'To zapewnia drobnoziarnistość: serwer nigdy nie jest globalnie blokowany na czas odczytu strony.',
])
story.append(P('Poniżej rzeczywisty fragment metody pozyskującej blokadę — to jest serce ochrony przed stanem wyścigu:'))
story.append(CODE(
"""public LockInfoDTO acquireEditLock(String title, String token, String userName) {
    Page p = require(title);
    long now = clock.now();
    p.lock().writeLock().lock();              // wyłączny dostęp do TEJ strony
    try {
        EditLock cur = p.editLock();
        if (cur != null && !cur.isExpired(now) && !cur.heldBy(token))
            throw new PageLockedException(cur.holderName(), ...);   // przegrani
        EditLock l = new EditLock(token, userName, now, now + leaseMs);
        p.setEditLock(l);                     // zwycięzca zakłada dzierżawę
        return toLockInfo(l, now);
    } finally {
        p.lock().writeLock().unlock();
    }
}"""))

story.append(H2('4.3. Wykorzystane mechanizmy synchronizacji'))
story.append(TBL([
   [td('Mechanizm', head=True), td('Gdzie', head=True), td('Po co', head=True)],
   [td('ReentrantReadWriteLock', mono=True), td('Page (per-strona)'), td('Współbieżne odczyty, wyłączne zapisy; drobnoziarniste blokowanie.')],
   [td('Blokada-dzierżawa (check-and-set)', mono=True), td('WikiStore.acquireEditLock'), td('Wzajemne wykluczenie edycji w czasie; jeden zwycięzca z N.')],
   [td('ConcurrentHashMap + putIfAbsent', mono=True), td('WikiStore (pages, users)'), td('Atomowe „utwórz jeśli nie istnieje” bez globalnej blokady.')],
   [td('Semaphore', mono=True), td('SessionManager'), td('Limit jednoczesnych klientów (licznik zasobów).')],
   [td('ExecutorService (pula wątków)', mono=True), td('NotificationService'), td('Powiadomienia poza wątkiem edycji — izolacja wolnych klientów.')],
   [td('Wątek daemon', mono=True), td('LockReaperDaemon'), td('Czyszczenie przeterminowanych blokad w tle.')],
   [td('volatile', mono=True), td('Session.lastSeen'), td('Bezpieczna widoczność pola między wątkami.')],
], [62*mm, 46*mm, 62*mm]))

story.append(H2('4.4. Te same sekcje krytyczne — warianty alternatywne'))
story.append(P('Aby pokazać różne podejścia do tego samego problemu, w kodzie źródłowym umieszczono '
   '<b>zakomentowane warianty alternatywne</b> przy każdej sekcji krytycznej. Poniżej ich porównanie:'))
story.append(TBL([
   [td('Problem', head=True), td('Użyte', head=True), td('Alternatywy (zakomentowane w kodzie)', head=True)],
   [td('Blokada edycji strony'), td('ReentrantReadWriteLock'), td('synchronized(p) — brak rozdziału R/W; ReentrantLock — bez R/W, ale tryLock/timeout; Semaphore(1) — bez właściciela i reentrancji.')],
   [td('Atomowe tworzenie strony'), td('ConcurrentHashMap.putIfAbsent'), td('synchronized(pages) + HashMap.containsKey/put; Collections.synchronizedMap (i tak wymaga zewnętrznego synchronized dla złożenia operacji).')],
   [td('Odczyt strony'), td('readLock (współdzielony)'), td('synchronized(p) — poprawny, ale serializuje czytelników.')],
   [td('Limit klientów'), td('Semaphore'), td('AtomicInteger z pętlą compare-and-set; synchronized na liczniku.')],
   [td('Rozsyłka powiadomień'), td('ExecutorService (asynchron.)'), td('Pętla synchroniczna — jeden zawieszony klient blokuje pozostałych.')],
], [38*mm, 44*mm, 88*mm]))

story.append(H2('4.5. Optymistyczna kontrola wersji (brak utraconych aktualizacji)'))
story.append(P('Każda strona ma numer wersji. Zapis (savePage) przyjmuje wersję bazową i — pod blokadą zapisu — '
   'sprawdza jej zgodność. Inkrementacja wersji oraz dopisanie rewizji do historii są niepodzielne, więc żadna '
   'aktualizacja nie ginie. Daje to podwójną ochronę: pesymistyczną (dzierżawa) i optymistyczną (wersja).'))
story.append(H2('4.6. Brak zakleszczeń (deadlock)'))
story.append(P('System jest <b>wolny od zakleszczeń z konstrukcji</b>: każda operacja trzyma <b>najwyżej jedną</b> '
   'blokadę strony naraz i nigdy nie zagnieżdża blokad różnych stron. Skoro nie powstaje cykl oczekiwań na blokady, '
   'zakleszczenie jest niemożliwe. ConcurrentHashMap zapewnia własną, niezależną synchronizację mapy.'))

story.append(PageBreak())

# ---- 5. PAMIĘĆ I WYDAJNOŚĆ ----
story.append(H1('5. Zarządzanie pamięcią i wydajność'))
story += BUL([
   'Wątek daemon (LockReaperDaemon) cyklicznie zwalnia przeterminowane dzierżawy — np. gdy klient zamknął edytor '
   'lub uległ awarii bez zwolnienia blokady. Jako wątek daemon nie blokuje zamknięcia JVM.',
   'Powiadomienia (callbacki RMI) wysyłane są na puli wątków, więc wolny lub „martwy” klient nie wstrzymuje wątku '
   'edycji; klient zgłaszający RemoteException jest automatycznie usuwany z listy subskrybentów.',
   'Blokowanie drobnoziarniste (per-strona, z rozdziałem odczyt/zapis) maksymalizuje przepustowość odczytów.',
   'Persystencja write-through: po każdej zmianie stan jest zapisywany do pliku tymczasowego i atomowo podmieniany '
   '(ATOMIC_MOVE), więc przerwany zapis nie uszkodzi danych. Dodatkowo hak zamknięcia zapisuje stan przy wyłączaniu.',
   'Klient wykonuje wywołania RMI poza wątkiem EDT (SwingWorker), więc interfejs nie zamarza podczas operacji sieciowych.',
   'Brak wycieków: przy wylogowaniu callback jest wyrejestrowywany i wyeksportowywany (unexportObject), a semafor '
   'zwalnia pozwolenie — zasoby wracają do puli.',
])

# ---- 6. FUNKCJONALNOŚĆ ----
story.append(H1('6. Funkcjonalność systemu'))
story += BUL([
   'Logowanie z rolami (administrator / użytkownik) i limitem jednoczesnych klientów.',
   'Przeglądanie i wyszukiwanie pełnotekstowe stron (po tytule i treści).',
   'Edycja z blokadą (jeden edytor naraz), z licznikiem czasu blokady i automatycznym odnawianiem.',
   'Podgląd Markdown (nagłówki, pogrubienie, kursywa, listy) oraz klikalne linki [[Strona]].',
   'Powiadomienia na żywo (callbacki RMI): panel „online” i „aktualnie edytowane”, automatyczne odświeżanie.',
   'Historia wersji: przeglądanie rewizji, porównanie różnic (diff) i przywracanie wcześniejszych wersji.',
   'Administracja: tworzenie/usuwanie kont, usuwanie stron, wymuszone odblokowanie strony.',
   'Tworzenie stron dostępne dla każdego zalogowanego użytkownika (rozszerzenie względem tematu — usuwanie pozostaje funkcją administratora).',
   'Konto: zmiana hasła. Interfejs: pasek menu, narzędzi, stanu oraz skróty klawiszowe.',
])

# ---- 7. INSTRUKCJA ----
story.append(H1('7. Instrukcja wdrożenia i obsługi (krok po kroku)'))
story.append(P('<b>Wymagania:</b> JDK 17 (testowano na Amazon Corretto 17; Java 8 również działa). Skrypty PowerShell '
   'automatycznie wyszukują JDK w katalogu %USERPROFILE%\\.jdks lub w zmiennej JAVA_HOME.'))
story.append(CODE(
""".\\build.ps1            # kompilacja src + test do out/ (UTF-8)
.\\run-server.ps1       # serwer: tworzy rejestr RMI na porcie 1099, wczytuje/seeduje dane
.\\run-client.ps1       # klient (Swing) — uruchom kilka instancji, aby pokazać współbieżność
.\\run-tests.ps1        # zestaw testów współbieżności i poprawności"""))
story.append(P('Domyślne konto administratora przy pierwszym uruchomieniu: <b>admin / admin123</b>. '
   'Aby zademonstrować współbieżność, należy uruchomić dwie instancje klienta i w obu otworzyć do edycji tę samą stronę.'))

# ---- 8. OGRANICZENIA I TESTY ----
story.append(H1('8. Ograniczenia i testy poprawności'))
story.append(P('<b>Maksymalna liczba jednoczesnych klientów:</b> ograniczona semaforem, domyślnie 50 (konfigurowalne w '
   'ServerConfig.MAX_CLIENTS). Po przekroczeniu limitu kolejne logowania są odrzucane z czytelnym komunikatem.'))
story.append(P('<b>Przechowywanie danych i restart:</b> stan (użytkownicy, strony, historia) zapisywany jest w czytelnym '
   'pliku wiki.json (zapis atomowy). Blokady edycji są stanem ulotnym i celowo nie są utrwalane. Po restarcie serwera '
   'dane są w pełni odtwarzane — potwierdza to test RestartPersistenceTest.'))
story.append(H2('Scenariusze i wyniki testów współbieżności'))
story.append(TBL([
   [td('Test', head=True), td('Co sprawdza', head=True), td('Wynik', head=True)],
   [td('ConcurrencyTest', mono=True), td('10 wątków jednocześnie pozyskuje blokadę tej samej strony.'), td('PASS — acquired=1, rejected=9')],
   [td('LostUpdateTest', mono=True), td('50 edytorów; brak utraconych aktualizacji.'), td('PASS — wersja=50, historia=50')],
   [td('ReadersWriterTest', mono=True), td('Wielu czytelników + pisarz; brak odczytu częściowego.'), td('PASS — brak „torn read”')],
   [td('CreatePageRaceTest', mono=True), td('20 wątków tworzy tę samą stronę.'), td('PASS — dokładnie 1 sukces')],
   [td('LockExpiryTest', mono=True), td('Reaper zwalnia przeterminowaną blokadę (zegar sterowany).'), td('PASS')],
   [td('RestoreRevisionTest', mono=True), td('Przywracanie rewizji jako nowej wersji.'), td('PASS')],
   [td('ForceUnlockTest', mono=True), td('Admin zdejmuje blokadę; inny może edytować.'), td('PASS')],
   [td('RestartPersistenceTest', mono=True), td('Zapis i odtworzenie stanu (restart).'), td('PASS')],
   [td('JSON / Hasła / Sesje / Powiadomienia / Markdown / Diff', mono=True), td('Testy jednostkowe pozostałych komponentów.'), td('PASS')],
], [58*mm, 78*mm, 34*mm]))
story.append(P('<b>Wynik zbiorczy zestawu:</b> 17 testów zaliczonych, 0 błędów (17 passed, 0 failed). Dodatkowo '
   'samodzielny test integracyjny po RMI (dwóch klientów) potwierdza, że stan wyścigu jest blokowany „przez sieć”, '
   'a powiadomienia zwrotne docierają: „B rejected with PageLockedException… / B received onPageChanged callback”.'))
story.append(P('<b>Ograniczenia:</b> wszyscy klienci łączą się z jednym serwerem (pojedynczy węzeł); kodek JSON obsługuje '
   'wymagany podzbiór typów; render Markdown obejmuje wybrany podzbiór składni. Założenia te są wystarczające dla '
   'zakresu zadania.'))

story.append(H1('9. Podsumowanie'))
story.append(P('WikiRMI realizuje pełny, działający system rozproszony z poprawną, drobnoziarnistą i wolną od '
   'zakleszczeń współbieżnością. Sekcje krytyczne chroni rozdzielony model (dzierżawa + ReentrantReadWriteLock), a '
   'kod zawiera edukacyjne warianty alternatywne tych samych rozwiązań. Całość jest pokryta zestawem 17 testów '
   '(w tym kluczowym testem stanu wyścigu) i dostępna w repozytorium: github.com/maciej-wawer/RMI.'))

# ---------------------------------------------------------------- build
def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont('PL', 8)
    canvas.setFillColor(colors.HexColor('#888'))
    canvas.drawString(20*mm, 12*mm, 'WikiRMI — Sprawozdanie')
    canvas.drawRightString(190*mm, 12*mm, 'str. %d' % doc.page)
    canvas.setStrokeColor(GRIDC); canvas.line(20*mm, 15*mm, 190*mm, 15*mm)
    canvas.restoreState()

doc = SimpleDocTemplate(OUT, pagesize=A4, leftMargin=20*mm, rightMargin=20*mm,
                        topMargin=18*mm, bottomMargin=20*mm, title='Sprawozdanie WikiRMI',
                        author='Maciej Wawer, Krystian Wasil, Marek Więcaszek')
doc.build(story, onFirstPage=footer, onLaterPages=footer)
print('WROTE', OUT)
