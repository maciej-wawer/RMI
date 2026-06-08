# -*- coding: utf-8 -*-
"""Generuje PDF ze scenariuszem prezentacji (10 min, 3 osoby, nacisk na wielowątkowość).
Uruchomienie:  python docs/generate_prezentacja.py   ->   docs/Prezentacja-WikiRMI.pdf
"""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase.pdfmetrics import registerFontFamily

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "Prezentacja-WikiRMI.pdf")
F = r"C:\Windows\Fonts"

def reg(n, f):
    p = os.path.join(F, f)
    if os.path.exists(p):
        pdfmetrics.registerFont(TTFont(n, p)); return True
    return False

reg('PL', 'arial.ttf'); reg('PL-Bold', 'arialbd.ttf')
hi = reg('PL-Italic', 'ariali.ttf'); hbi = reg('PL-BoldItalic', 'arialbi.ttf')
reg('Mono', 'consola.ttf')
registerFontFamily('PL', normal='PL', bold='PL-Bold',
                   italic='PL-Italic' if hi else 'PL', boldItalic='PL-BoldItalic' if hbi else 'PL-Bold')

ACCENT = colors.HexColor('#2D6CDF'); DARK = colors.HexColor('#1F2937')
LIGHT = colors.HexColor('#EAF1FF'); GREEN = colors.HexColor('#2E7D32')

S = {
 'h1':   ParagraphStyle('h1', fontName='PL-Bold', fontSize=13, leading=16, textColor=colors.white,
                        backColor=ACCENT, borderPadding=(6, 8, 6, 8), spaceBefore=12, spaceAfter=6),
 'h2':   ParagraphStyle('h2', fontName='PL-Bold', fontSize=11, leading=14, textColor=ACCENT, spaceBefore=7, spaceAfter=3),
 'body': ParagraphStyle('body', fontName='PL', fontSize=9.5, leading=13.5, textColor=DARK, spaceAfter=3),
 'bul':  ParagraphStyle('bul', fontName='PL', fontSize=9.5, leading=13, textColor=DARK, leftIndent=14, bulletIndent=4, spaceAfter=1.5),
 'say':  ParagraphStyle('say', fontName='PL-Italic' if hi else 'PL', fontSize=9.5, leading=13.5,
                        textColor=colors.HexColor('#0B2545'), backColor=colors.HexColor('#F2F5FB'),
                        borderPadding=(5, 7, 5, 7), leftIndent=2, spaceBefore=2, spaceAfter=5),
 'tag':  ParagraphStyle('tag', fontName='PL-Bold', fontSize=9.5, leading=13, textColor=GREEN, spaceBefore=4, spaceAfter=2),
}

def P(t): return Paragraph(t, S['body'])
def H1(t): return Paragraph(t, S['h1'])
def H2(t): return Paragraph(t, S['h2'])
def SAY(t): return Paragraph('„' + t + '”', S['say'])
def TAG(t): return Paragraph(t, S['tag'])
def BUL(items): return [Paragraph(i, S['bul'], bulletText='•') for i in items]
def CODE(t): return '<font name="Mono" size="8.5" color="#0B2545">' + t + '</font>'

story = []

# nagłówek
story.append(Paragraph('WikiRMI — scenariusz prezentacji (10 minut)', S['h1']))
story.append(P('<b>Zespół:</b> Maciej Wawer, Krystian Wasil, Marek Więcaszek &nbsp;·&nbsp; '
               '<b>Temat 9</b> — system typu wiki &nbsp;·&nbsp; <b>Nacisk:</b> wielowątkowość (4 mechanizmy + RMI) &nbsp;·&nbsp; ~7 slajdów'))

# CZĘŚĆ 1
story.append(H1('CZĘŚĆ 1 — Maciej Wawer · ~3 min · Architektura rozproszona (RMI) i skąd biorą się wątki'))
story.append(H2('Slajd 1 — tytuł (20 s)'))
story.append(SAY('Dzień dobry. Przedstawiamy WikiRMI — rozproszony system typu wiki, temat 9. Administrator tworzy '
                 'szkielety stron i konta, a użytkownicy je modyfikują. Komunikacja działa przez RMI, a sercem '
                 'projektu jest bezpieczna współbieżność.'))
story.append(H2('Slajd 2 — schemat Klient–Rejestr RMI–Serwer (~1:30)'))
story += BUL([
    'Trzy warstwy: ' + CODE('common') + ' (kontrakt), ' + CODE('server') + ', ' + CODE('client') + '.',
    'Interfejs zdalny ' + CODE('WikiService extends Remote') + ', metody ' + CODE('throws RemoteException') + ' — ' + CODE('WikiService.java:15') + '.',
    'Serwer eksportuje ' + CODE('WikiServiceImpl extends UnicastRemoteObject') + ' i rejestruje: ' + CODE('Naming.rebind("//localhost:1099/WikiService")') + ' — ' + CODE('WikiServer.java:35-36') + '.',
    'Klient pobiera stub: ' + CODE('Naming.lookup(...)') + ' i woła metody jak lokalne — ' + CODE('WikiClientController.java:35') + '.',
    'RMI działa też w drugą stronę — callbacki serwer→klient (powiadomienia na żywo).',
])
story.append(TAG('Most do wielowątkowości — KLUCZOWE zdanie:'))
story.append(SAY('I tu pojawia się problem współbieżności: RMI obsługuje każde wywołanie klienta w osobnym wątku po '
                 'stronie serwera. Gdy kilku użytkowników działa jednocześnie, wiele wątków serwera sięga po te same '
                 'dane — bez synchronizacji powstałby stan wyścigu. Jak to rozwiązaliśmy — opowie Krystian.'))

# CZĘŚĆ 2
story.append(H1('CZĘŚĆ 2 — Krystian Wasil · ~4 min · Wielowątkowość — serce projektu (4 mechanizmy)'))
story.append(H2('Slajd 3 — sekcja krytyczna (~50 s)'))
story += BUL([
    'Problem: dwóch użytkowników edytuje tę samą stronę naraz → utrata zapisu (lost update) lub odczyt niespójnej strony.',
])
story.append(SAY('Cały rdzeń współbieżności umieściliśmy w jednym pliku — WikiStore.java — i celowo użyliśmy w nim '
                 'czterech klasycznych mechanizmów, każdy w osobnej, opisanej sekcji.'))
story.append(H2('Slajd 4 — cztery mechanizmy (najważniejszy slajd, ~2:30)'))
story += BUL([
    '<b>1. BLOKADY — ReentrantReadWriteLock</b> (' + CODE('Page.java:23') + ', ' + CODE('WikiStore.java:266') + '): '
    'każda strona ma własną blokadę (drobnoziarniste); ' + CODE('readLock()') + ' = wielu czytelników, ' + CODE('writeLock()') + ' = jeden piszący.',
    '&nbsp;&nbsp;&nbsp;Sekcja krytyczna „sprawdź-i-ustaw" blokady edycji (' + CODE('WikiStore.java:321') + '): '
    'z N wątków DOKŁADNIE JEDEN zakłada dzierżawę, reszta dostaje ' + CODE('PageLockedException') + '. To nasza ochrona przed race condition.',
    '<b>2. MONITORY — synchronized</b> (' + CODE('WikiStore.java:133') + '): wspólny licznik zapisów; '
    'bez ' + CODE('synchronized') + ' „licznik++" gubiłby zliczenia (odczyt-dodaj-zapis nie jest atomowy).',
    '<b>3. SEMAFORY — Semaphore</b> (' + CODE('WikiStore.java:51, 67') + '): limit jednoczesnych klientów; '
    'logowanie ' + CODE('tryAcquire()') + ', wylogowanie ' + CODE('release()') + '.',
    '<b>4. WĄTKI — Thread daemon + pula</b> (' + CODE('WikiStore.java:153') + ', ' + CODE('NotificationService.java:29') + '): '
    'wątek daemon zwalnia przeterminowane blokady; pula wątków (ExecutorService) wysyła powiadomienia poza wątkiem edycji.',
])
story.append(TAG('Dwa mocne argumenty na koniec (~40 s):'))
story += BUL([
    '<b>Brak zakleszczeń:</b> każda operacja trzyma najwyżej jedną blokadę strony naraz → brak cyklu oczekiwań (' + CODE('WikiStore.java:33-35') + ').',
    '<b>Brak lost update:</b> dodatkowo optymistyczna kontrola wersji — zapis sprawdza numer wersji.',
])
story.append(SAY('Pokażemy teraz, że to naprawdę działa — Marek.'))

# CZĘŚĆ 3
story.append(H1('CZĘŚĆ 3 — Marek Więcaszek · ~3 min · Demo na żywo + testy współbieżności + dane'))
story.append(H2('Slajd 5 — DEMO NA ŻYWO (~1:30) · dwa okna klienta już zalogowane'))
story += BUL([
    'W obu oknach zaznacz „Strona główna" → w kliencie 1 kliknij Edytuj.',
    'W kliencie 2 kliknij Edytuj tę samą stronę → komunikat „Strona jest aktualnie edytowana przez \'admin\'…" → race condition zablokowany na żywo.',
    'W kliencie 1 zmień tekst i Zapisz → lista w kliencie 2 odświeża się automatycznie (callback RMI).',
])
story.append(SAY('Drugi klient nie mógł wejść w edycję — blokada zadziałała, a powiadomienie przyszło przez RMI.'))
story.append(H2('Slajd 6 — testy współbieżności (~1:00)'))
story += BUL([
    CODE('ConcurrencyTest') + ': 10 wątków próbuje naraz → 1 zdobywa blokadę, 9 odrzuconych (dowód braku wyścigu).',
    CODE('LostUpdateTest') + ': 50 edytorów → wersja = 50, nic nie zgubione.',
    'Łącznie 17/17 testów + test integracyjny po RMI (wyścig „przez sieć").',
    'Dane w ' + CODE('wiki.json') + ' (zapis atomowy) — po restarcie serwera stan jest odtwarzany; limit klientów pilnuje semafor.',
])
story.append(H2('Slajd 7 — podsumowanie (~30 s)'))
story.append(SAY('Podsumowując: kompletny system rozproszony RMI z poprawną, drobnoziarnistą i wolną od zakleszczeń '
                 'współbieżnością, pokazaną na czterech mechanizmach. Kod i dokumentacja są w repozytorium. '
                 'Dziękujemy — zapraszamy do pytań.'))

# wskazówki
story.append(H1('Wskazówki na obronę — każdy zna swój kawałek'))
story += BUL([
    '<b>Maciej</b> — RMI: czemu ' + CODE('extends Remote') + ' / ' + CODE('UnicastRemoteObject') + ', różnica ' + CODE('Naming.rebind') + ' vs rejestr, czemu callbacki.',
    '<b>Krystian</b> — różnica monitor vs blokada vs semafor; czemu ReadWriteLock zamiast synchronized (przepustowość odczytów); argument o braku deadlocka.',
    '<b>Marek</b> — jak testy dowodzą braku wyścigu (' + CODE('CountDownLatch') + ', 10 wątków); jak działa restart i limit klientów.',
    'Bądźcie gotowi zmienić kod na żywo (np. limit semafora = 1 i pokazać odmowę logowania, albo skrócić czas blokady).',
])

doc = SimpleDocTemplate(OUT, pagesize=A4, leftMargin=18*mm, rightMargin=18*mm, topMargin=16*mm, bottomMargin=16*mm,
                        title='Prezentacja WikiRMI (10 min)', author='Maciej Wawer, Krystian Wasil, Marek Więcaszek')
doc.build(story)
print('WROTE', OUT)
