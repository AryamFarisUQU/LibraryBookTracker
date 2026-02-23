import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class LibraryBookTracker {

    private static int validRecords = 0;
    private static int searchResults = 0;
    private static int booksAdded = 0;
    private static int errorCount = 0;

    private static String errorLogPath = "errors.log";

    /**
     * Main entry point of the program.
     * @param args command-line arguments:
     *             args[0] = catalog file path
     *             args[1] = operation argument
     */
    public static void main(String[] args) {

        try {

            if (args.length < 2) {
                throw new InsufficientArgumentsException(
                        "Expected 2 arguments: <catalogFile.txt> <operationArgument>");
            }

            String catalogPath = args[0];

            if (!catalogPath.endsWith(".txt")) {
                throw new InvalidFileNameException(
                        "Catalog file must end with .txt, got: " + catalogPath);
            }

            File catalogFile = new File(catalogPath);
            String parentDir = catalogFile.getParent();
            errorLogPath = (parentDir != null ? parentDir + File.separator : "") + "errors.log";

            ensureFileExists(catalogFile);

            List<Book> books = loadCatalog(catalogFile);

            String operation = args[1];

            if (isNewBookRecord(operation)) {
                addBook(operation, books, catalogFile);
            } else if (isISBNSearch(operation)) {
                searchByISBN(operation, books);
            } else {
                searchByTitle(operation, books);
            }

        } catch (InsufficientArgumentsException | InvalidFileNameException e) {
            System.out.println("Error: " + e.getMessage());
            errorCount++;
        } catch (DuplicateISBNException e) {
            System.out.println("Error: " + e.getMessage());
            errorCount++;
        } catch (IOException e) {
            System.out.println("IO Error: " + e.getMessage());
            errorCount++;
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getMessage());
            errorCount++;
        } finally {

            System.out.println("\n--- Statistics ---");
            System.out.println("Valid records processed : " + validRecords);
            System.out.println("Search results          : " + searchResults);
            System.out.println("Books added             : " + booksAdded);
            System.out.println("Errors encountered      : " + errorCount);

            System.out.println("\nThank you for using the Library Book Tracker.");
        }
    }

    /**
     * Ensures the catalog file and its parent directories exist.
     * @param file catalog file
     * @throws IOException if file creation fails
     */
    private static void ensureFileExists(File file) throws IOException {
        if (!file.exists()) {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            file.createNewFile();
        }
    }

    /**
     * Reads and validates all lines from the catalog file.
     * @param catalogFile file containing book records
     * @return list of valid Book objects
     * @throws IOException if file reading fails
     */
    private static List<Book> loadCatalog(File catalogFile) throws IOException {

        List<Book> books = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(catalogFile))) {

            String line;
            while ((line = reader.readLine()) != null) {

                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    Book book = parseLine(line);
                    books.add(book);
                    validRecords++;
                } catch (BookCatalogException e) {
                    errorCount++;
                    logError(line, e);
                }
            }
        }

        return books;
    }

    /**
     * Parses and validates a single catalog line.
     * @param line catalog line in format Title:Author:ISBN:Copies
     * @return Book object if valid
     * @throws BookCatalogException if validation fails
     */
    private static Book parseLine(String line) throws BookCatalogException {

        String[] parts = line.split(":");

        if (parts.length != 4) {
            throw new MalformedBookEntryException(
                    "Expected 4 fields (Title:Author:ISBN:Copies)");
        }

        String title = parts[0].trim();
        String author = parts[1].trim();
        String isbn = parts[2].trim();
        String copiesStr = parts[3].trim();

        if (title.isEmpty()) {
            throw new MalformedBookEntryException("Title is empty");
        }

        if (author.isEmpty()) {
            throw new MalformedBookEntryException("Author is empty");
        }

        if (!isbn.matches("\\d{13}")) {
            throw new InvalidISBNException("ISBN must be exactly 13 digits");
        }

        int copies;

        try {
            copies = Integer.parseInt(copiesStr);
        } catch (NumberFormatException e) {
            throw new MalformedBookEntryException("Copies is not an integer");
        }

        if (copies <= 0) {
            throw new MalformedBookEntryException("Copies must be greater than 0");
        }

        return new Book(title, author, isbn, copies);
    }

    /**
     * Determines whether the argument is an ISBN search.
     * @param arg operation argument
     * @return true if it matches 13-digit ISBN format
     */
    private static boolean isISBNSearch(String arg) {
        return arg.matches("\\d{13}");
    }

    /**
     * Determines whether the argument is a new book record.
     * @param arg operation argument
     * @return true if it matches Title:Author:ISBN:Copies format
     */
    private static boolean isNewBookRecord(String arg) {
        return arg.contains(":") && arg.split(":").length == 4;
    }

    /**
     * Performs title keyword search.
     * @param keyword search keyword
     * @param books list of books
     */
    private static void searchByTitle(String keyword, List<Book> books) {

        String lower = keyword.toLowerCase();
        List<Book> found = new ArrayList<>();

        for (Book b : books) {
            if (b.getTitle().toLowerCase().contains(lower)) {
                found.add(b);
            }
        }

        searchResults = found.size();

        printHeader();
        for (Book b : found) {
            printBook(b);
        }
    }

    /**
     * Performs ISBN search.
     * @param isbn ISBN value
     * @param books list of books
     * @throws DuplicateISBNException if multiple books found
     */
    private static void searchByISBN(String isbn, List<Book> books)
            throws DuplicateISBNException {

        List<Book> found = new ArrayList<>();

        for (Book b : books) {
            if (b.getIsbn().equals(isbn)) {
                found.add(b);
            }
        }

        if (found.size() > 1) {
            throw new DuplicateISBNException("Duplicate ISBN detected");
        }

        searchResults = found.size();

        printHeader();

        if (!found.isEmpty()) {
            printBook(found.get(0));
        }
    }

    /**
     * Adds a new book to the catalog.
     * @param record book record string
     * @param books list of books
     * @param catalogFile catalog file
     * @throws IOException if writing fails
     */
    private static void addBook(String record, List<Book> books, File catalogFile)
            throws IOException {

        try {

            Book newBook = parseLine(record);

            books.add(newBook);
            books.sort(Comparator.comparing(b -> b.getTitle().toLowerCase()));

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(catalogFile))) {
                for (Book b : books) {
                    writer.write(b.toFileLine());
                    writer.newLine();
                }
            }

            booksAdded = 1;

            printHeader();
            printBook(newBook);

        } catch (BookCatalogException e) {
            errorCount++;
            logError(record, e);
        }
    }

    /**
     * Logs an error into errors.log file.
     * @param offendingText text causing the error
     * @param e exception object
     */
    private static void logError(String offendingText, Exception e) {

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        String logLine = "[" + timestamp + "] INVALID LINE: \""
                + offendingText + "\" - "
                + e.getClass().getSimpleName() + ": " + e.getMessage();

        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(errorLogPath, true))) {

            writer.write(logLine);
            writer.newLine();

        } catch (IOException ignored) {
        }
    }

    private static void printHeader() {
        System.out.printf("%-30s %-20s %-15s %5s%n",
                "Title", "Author", "ISBN", "Copies");
    }

    private static void printBook(Book b) {
        System.out.printf("%-30s %-20s %-15s %5d%n",
                b.getTitle(), b.getAuthor(), b.getIsbn(), b.getCopies());
    }
}