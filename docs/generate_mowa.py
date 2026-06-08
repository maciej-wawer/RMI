# -*- coding: utf-8 -*-
"""PDF: gotowa mowa do nauczenia na pamięć (3 osoby) + fiszki Q&A na obronę.
Uruchomienie: python docs/generate_mowa.py  ->  docs/Mowa-i-pytania-WikiRMI.pdf
"""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_JUSTIFY
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase.pdfmetrics import registerFontFamily

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "Mowa-i-pytania-WikiRMI.pdf")
F = r"C:\Windows\Fonts"
def reg(n, f):
    p = os.path.join(F, f)
    if os.path.exists(p): pdfmetrics.registerFont(TTFont(n, p)); return True
    return False
reg('PL', 'arial.ttf'); reg('PL-Bold', 'arialbd.ttf')
hi = reg('PL-Italic', 'ariali.ttf'); hbi = reg('PL-BoldItalic', 'arialbi.ttf')
registerFontFamily('PL', normal='PL', bold='PL-Bold',
                   italic='PL-Italic' if hi else 'PL', boldItalic='PL-BoldItalic' if hbi else 'PL-Bold')

ACCENT = colors.HexColor('#2D6CDF'); DARK = colors.HexColor('#1F2937')
S = {
 'h1':  ParagraphStyle('h1', fontName='PL-Bold', fontSize=13, leading=16, textColor=colors.white,
                       backColor=ACCENT, borderPadding=(6,8,6,8), spaceBefore=12, spaceAfter=8),
 'body':ParagraphStyle('body', fontName='PL', fontSize=10.5, leading=15.5, textColor=DARK, alignment=TA_JUSTIFY, spaceAfter=7),
 'q':   ParagraphStyle('q', fontName='PL-Bold', fontSize=10, leading=13.5, textColor=ACCENT, spaceBefore=7, spaceAfter=2),
 'a':   ParagraphStyle('a', fontName='PL', fontSize=10, leading=14, textColor=DARK, leftIndent=10, spaceAfter=3),
 'note':ParagraphStyle('note', fontName='PL-Italic' if hi else 'PL', fontSize=9, leading=12, textColor=colors.HexColor('#666'), spaceAfter=6),
}
def P(t): return Paragraph(t, S['body'])
def H1(t): return Paragraph(t, S['h1'])

story = []
story.append(H1('WikiRMI — mowa do nauczenia na pamięć + pytania na obronę'))
story.append(Paragraph('Zespół: Maciej Wawer, Krystian Wasil, Marek Więcaszek · ~10 min · nacisk na wielowątkowość', S['note']))

# ---- OSOBA 1 ----
story.append(H1('OSOBA 1 — Maciej Wawer (~3 min)'))
story.append(P('Dzień dobry. Chcielibyśmy przedstawić nasz projekt — <b>WikiRMI</b>, czyli rozproszony system typu wiki. '
  'Wybraliśmy temat dziewiąty: administrator tworzy szkielety stron oraz konta użytkowników, a użytkownicy mogą te '
  'strony przeglądać i modyfikować. Całość działa jako aplikacja <b>rozproszona</b> — klient i serwer to osobne '
  'programy, które komunikują się przez sieć za pomocą mechanizmu <b>RMI</b>, czyli zdalnego wywoływania metod.'))
story.append(P('Zacznę od architektury. Projekt dzieli się na trzy warstwy: wspólny kontrakt, serwer i klienta. '
  'Kontrakt to interfejs zdalny — w Javie dziedziczy on po interfejsie Remote, a każda jego metoda może zgłosić '
  'wyjątek RemoteException. Serwer dostarcza implementację tego interfejsu; nasza klasa serwera dziedziczy po '
  'UnicastRemoteObject, dzięki czemu staje się <b>obiektem zdalnym</b>, dostępnym przez sieć.'))
story.append(P('Jak to się spina? Serwer tworzy <b>rejestr RMI</b> i rejestruje w nim swój obiekt poleceniem '
  'Naming.rebind, pod adresem w formacie „lokalizator, host, dwukropek, port, nazwa". Klient pobiera z rejestru '
  '<b>namiastkę</b>, czyli stub, poleceniem Naming.lookup, i od tej pory wywołuje metody serwera tak, jakby były '
  'lokalne — choć w rzeczywistości wykonują się na serwerze. RMI działa u nas w obie strony: serwer również wywołuje '
  'metody po stronie klienta, czyli tak zwane callbacki, żeby na bieżąco wysyłać powiadomienia o zmianach.'))
story.append(P('I tu dochodzimy do sedna. <b>RMI obsługuje każde wywołanie klienta w osobnym wątku</b> po stronie '
  'serwera. To znaczy, że gdy kilku użytkowników działa jednocześnie, wiele wątków serwera sięga po te same dane — '
  'na przykład po tę samą stronę. Bez synchronizacji powstałby <b>stan wyścigu</b>, a jeden zapis mógłby nadpisać '
  'drugi. Jak rozwiązaliśmy ten problem — opowie teraz Krystian.'))

# ---- OSOBA 2 ----
story.append(H1('OSOBA 2 — Krystian Wasil (~4 min) — część najważniejsza'))
story.append(P('Dziękuję. Najważniejsza w naszym projekcie jest poprawna <b>wielowątkowość</b>, więc poświęcę jej '
  'najwięcej czasu. Główna sekcja krytyczna to sytuacja, w której <b>dwóch użytkowników edytuje tę samą stronę w tym '
  'samym momencie</b>. Gdybyśmy tego nie kontrolowali, doszłoby do utraty zapisu albo odczytu strony w stanie '
  'niespójnym. Cały rdzeń współbieżności umieściliśmy świadomie w <b>jednym pliku — klasie WikiStore</b> — i użyliśmy '
  'w nim <b>czterech klasycznych mechanizmów</b>, każdy w osobnej, opisanej sekcji.'))
story.append(P('<b>Pierwszy mechanizm to blokady — ReentrantReadWriteLock.</b> Każda strona ma własną blokadę '
  'odczytu i zapisu. To blokowanie drobnoziarniste — blokujemy pojedynczą stronę, a nie cały serwer. Blokada odczytu '
  'pozwala wielu użytkownikom czytać tę samą stronę jednocześnie, a blokada zapisu jest wyłączna — tylko jeden wątek '
  'może zapisywać. Tu jest serce ochrony przed wyścigiem: pozyskanie blokady edycji działa metodą „sprawdź i ustaw" '
  'pod blokadą zapisu. Dzięki temu, gdy dziesięć wątków równocześnie spróbuje wejść w edycję, <b>dokładnie jeden</b> '
  'zakłada blokadę, a pozostali dostają wyjątek PageLockedException.'))
story.append(P('<b>Drugi mechanizm to monitory — słowo kluczowe synchronized.</b> Używamy go do wspólnego licznika '
  'zapisów. To klasyczny monitor: blok synchronized wpuszcza tylko jeden wątek naraz. Gdyby go nie było, zwykłe '
  '„licznik plus plus" gubiłoby zliczenia, bo operacja odczyt–dodaj–zapis nie jest niepodzielna.'))
story.append(P('<b>Trzeci mechanizm to semafory — klasa Semaphore.</b> Ogranicza liczbę jednocześnie zalogowanych '
  'klientów. Logowanie pobiera jedno pozwolenie metodą tryAcquire, a wylogowanie je oddaje metodą release. Gdy '
  'pozwoleń braknie — serwer odmawia kolejnego logowania.'))
story.append(P('<b>Czwarty mechanizm to wątki.</b> Mamy wątek demon, który w tle cyklicznie zwalnia przeterminowane '
  'blokady — na przykład gdy klient się zawiesił. Mamy też pulę wątków, która wysyła powiadomienia do klientów poza '
  'wątkiem edycji, żeby jeden wolny klient nie blokował pozostałych.'))
story.append(P('Na koniec dwa argumenty. Po pierwsze — <b>brak zakleszczeń</b>: każda operacja trzyma najwyżej jedną '
  'blokadę strony naraz i nie zagnieżdża blokad, więc cykl oczekiwań nie może powstać. Po drugie — <b>brak utraconych '
  'zapisów</b>: dodatkowo stosujemy optymistyczną kontrolę wersji. Pokażemy teraz, że to działa — Marek.'))

# ---- OSOBA 3 ----
story.append(H1('OSOBA 3 — Marek Więcaszek (~3 min)'))
story.append(P('Dziękuję. Przejdźmy do <b>demonstracji na żywo</b>. Mamy uruchomione dwa okna klienta, oba zalogowane '
  'na to samo konto. W pierwszym oknie wybieram stronę i klikam Edytuj — od tej chwili ten klient trzyma blokadę. '
  'Teraz w drugim oknie próbuję otworzyć tę samą stronę do edycji — i dostaję komunikat: „Strona jest aktualnie '
  'edytowana przez admina". To jest <b>stan wyścigu zablokowany na żywo</b>. Teraz w pierwszym oknie zapisuję zmianę '
  '— i lista w drugim oknie <b>odświeżyła się sama</b>. To zadziałał callback RMI, czyli powiadomienie z serwera do klienta.'))
story.append(P('Tę samą poprawność potwierdzają <b>testy współbieżności</b>. Najważniejszy uruchamia <b>dziesięć '
  'wątków</b>, które jednocześnie próbują zająć tę samą stronę — i za każdym razem jeden zdobywa blokadę, a dziewięć '
  'zostaje odrzuconych. Drugi test uruchamia <b>pięćdziesięciu</b> edytorów po kolei i sprawdza, że numer wersji '
  'końcowej to dokładnie pięćdziesiąt — czyli żaden zapis nie ginie. Łącznie mamy <b>siedemnaście testów i wszystkie '
  'przechodzą</b>, w tym test integracyjny sprawdzający wyścig „przez sieć", po RMI.'))
story.append(P('Na koniec dane i trwałość. Stan zapisujemy do czytelnego pliku JSON, atomowo, więc przerwany zapis '
  'nie uszkodzi danych. Po <b>restarcie serwera</b> wszystkie strony, użytkownicy i historia są w pełni odtwarzane, '
  'a liczbę jednoczesnych klientów pilnuje semafor.'))
story.append(P('Podsumowując: zbudowaliśmy kompletny, działający system <b>rozproszony w RMI</b> z poprawną, '
  'drobnoziarnistą i <b>wolną od zakleszczeń wielowątkowością</b>, pokazaną na czterech klasycznych mechanizmach. '
  'Dziękujemy za uwagę i zapraszamy do pytań.'))

# ---- FISZKI Q&A ----
story.append(H1('Fiszki — możliwe pytania komisji i krótkie odpowiedzi'))
qa = [
 ("Czym różni się monitor (synchronized) od blokady (Lock)?",
  "Monitor (synchronized) jest wbudowany w każdy obiekt, prosty, ale 'wszystko albo nic' — zwalnia się automatycznie na końcu bloku. Lock (np. ReentrantLock, ReentrantReadWriteLock) jest jawny, daje więcej: rozdział odczytu/zapisu, tryLock z czasem, lockInterruptibly."),
 ("Dlaczego ReentrantReadWriteLock zamiast zwykłego synchronized?",
  "Bo aplikacja jest 'dużo odczytów, mało zapisów'. ReadWriteLock pozwala wielu czytelnikom czytać RÓWNOCZEŚNIE, a synchronized serializowałby każdy odczyt — gorsza przepustowość."),
 ("Co to jest stan wyścigu (race condition) i jak go zapobiegacie?",
  "To sytuacja, gdy wynik zależy od kolejności przeplotu wątków. Zapobiegamy sekcją krytyczną 'sprawdź-i-ustaw' blokady edycji pod writeLock — z N wątków dokładnie jeden zakłada blokadę."),
 ("Dlaczego nie ma zakleszczeń (deadlock)?",
  "Bo każda operacja trzyma najwyżej JEDNĄ blokadę strony naraz i nigdy nie zagnieżdża blokad — nie powstaje cykl oczekiwań, a bez cyklu deadlock jest niemożliwy."),
 ("Czym jest semafor i do czego go użyliście?",
  "Semafor to licznik pozwoleń. Użyliśmy go do limitu jednoczesnych klientów: logowanie pobiera pozwolenie (tryAcquire), wylogowanie oddaje (release); brak pozwoleń = odmowa logowania."),
 ("Jak RMI radzi sobie z wielowątkowością?",
  "Serwer RMI obsługuje każde przychodzące wywołanie w OSOBNYM wątku z puli. Dlatego implementacja musi być bezpieczna wątkowo — i to my zapewniamy w WikiStore."),
 ("Co to UnicastRemoteObject, stub i rejestr RMI?",
  "UnicastRemoteObject to baza obiektu zdalnego (eksport przez sieć). Stub to lokalna 'namiastka' obiektu zdalnego u klienta. Rejestr RMI to katalog nazwa→obiekt, w którym serwer rejestruje usługę (Naming.rebind), a klient ją odnajduje (Naming.lookup)."),
 ("Czym są callbacki w RMI?",
  "To wywołania w drugą stronę: serwer wywołuje metodę po stronie klienta. Klient implementuje drugi interfejs zdalny (WikiClientCallback) i rejestruje się; serwer powiadamia go o zmianach (np. że strona została zapisana)."),
 ("Co to lost update i jak go unikacie?",
  "Lost update = jedna aktualizacja nadpisuje drugą i ginie. Unikamy podwójnie: pesymistycznie (tylko posiadacz blokady zapisuje) i optymistycznie (zgodność numeru wersji). Inkrementacja wersji + dopis do historii są niepodzielne."),
 ("Po co wątek daemon i czym różni się od zwykłego?",
  "Wątek demon sprząta w tle przeterminowane blokady (np. po awarii klienta). Różnica: wątek demon NIE wstrzymuje zamknięcia JVM — gdy zostaną tylko demony, program się kończy."),
 ("Po co volatile i ConcurrentHashMap?",
  "volatile zapewnia bezpieczną WIDOCZNOŚĆ pola między wątkami (tu: czas ostatniej aktywności sesji). ConcurrentHashMap to mapa bezpieczna wątkowo; putIfAbsent daje atomowe 'utwórz, jeśli nie istnieje' bez globalnej blokady."),
 ("Jaka jest maksymalna liczba jednoczesnych klientów i jak reaguje system na restart?",
  "Limit pilnuje semafor — domyślnie 50, konfigurowalne. Po restarcie serwer wczytuje stan z pliku wiki.json (zapis atomowy), więc strony, konta i historia są w pełni odtwarzane; blokady są ulotne i nie są utrwalane."),
]
for q, a in qa:
    story.append(Paragraph("P: " + q, S['q']))
    story.append(Paragraph("O: " + a, S['a']))

doc = SimpleDocTemplate(OUT, pagesize=A4, leftMargin=18*mm, rightMargin=18*mm, topMargin=16*mm, bottomMargin=16*mm,
                        title='Mowa i pytania — WikiRMI', author='Maciej Wawer, Krystian Wasil, Marek Więcaszek')
doc.build(story)
print('WROTE', OUT)
