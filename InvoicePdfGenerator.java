import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.svg.converter.SvgConverter;

public class InvoicePdfGenerator {

	private static final Path INPUT = Path.of("input");
	private static final Path OUTPUT = Path.of("output");
	private static final Path ARCHIVE = Path.of("archive");
	private static final Path ERRORS = Path.of("error");
	private static final String INVOICE = "Invoice__r.";
	private static final String ACCOUNT = INVOICE + "Account__r.";
	private static final Path LOGO = Path.of("logo.svg");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final String AMOUNT_FORMAT = "#,##0.00";
	private static final String COMPANY_NAME = "Carrier1 GmbH";
	private static final String COMPANY_ADDRESS = "Fritz-Vomfelde-Str. 34 · 40547 Düsseldorf";
	private static final String FOOTER = COMPANY_NAME + "\n" + COMPANY_ADDRESS + " · Deutschland\n"
			+ "AG Düsseldorf, HRB 111266 · USt-IdNr.: DE459619164\n" + "Geschäftsführer: Philipp Hoffmann\n"
			+ "Volksbank im Rheinland eG · IBAN: DE87 3106 0517 7000 8780 27 · BIC: GENODED1MRB";

	public static void main(String[] args) throws IOException {
		try (DirectoryStream<Path> files = Files.newDirectoryStream(INPUT, "*.csv")) {
			for (Path file : files) {
				Path destination = ARCHIVE;

				try {
					generate(file, OUTPUT);
				} catch (Exception e) {
					System.err.printf("%s: %s%n", file, e);
					destination = ERRORS;
				}

				Files.move(file, destination.resolve(file.getFileName()));
			}
		}
	}

	public static void generate(Path inputFile, Path outputDirectory) throws Exception {
		CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().get();

		String currentNumber = null;

		List<Map<String, String>> positions = new ArrayList<>();

		try (BufferedReader reader = Files.newBufferedReader(inputFile); CSVParser csv = format.parse(reader)) {
			for (CSVRecord record : csv) {
				String number = record.get(INVOICE + "Name");

				if (currentNumber != null && !currentNumber.equals(number)) {
					writePdf(positions, outputDirectory);
					positions.clear();
				}

				currentNumber = number;

				positions.add(record.toMap());
			}
		}

		if (!positions.isEmpty()) {
			writePdf(positions, outputDirectory);
		}
	}

	private static void writePdf(List<Map<String, String>> positions, Path outputDirectory) throws Exception {
		Map<String, String> invoice = positions.get(0);
		String invoiceNumber = invoice.get(INVOICE + "Name");
		String customerNumber = invoice.get(ACCOUNT + "Customer_Number__c");
		String dueDate = date(invoice.get(INVOICE + "Due_Date__c"));

		Path target = outputDirectory.resolve(invoiceNumber + ".pdf");

		PdfFont regularFont = PdfFontFactory.createFont();
		PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
		PdfWriter writer = new PdfWriter(target.toFile());
		PdfDocument pdf = new PdfDocument(writer);

		try (Document document = new Document(pdf, PageSize.A4, false)) {
			document.setMargins(51, 71, 95, 71);
			document.setFont(regularFont);
			document.setFontSize(10);

			String logoSvg = Files.readString(LOGO);
			Image logo = new Image(SvgConverter.convertToXObject(logoSvg, pdf));
			logo.setWidth(142);
			logo.setMarginBottom(45);
			logo.setHorizontalAlignment(HorizontalAlignment.RIGHT);
			document.add(logo);

			String customerName = invoice.get(ACCOUNT + "Name");
			String street = invoice.get(ACCOUNT + "BillingStreet");
			String city = invoice.get(ACCOUNT + "BillingPostalCode") + " " + invoice.get(ACCOUNT + "BillingCity");

			Paragraph sender = new Paragraph(COMPANY_NAME + " · " + COMPANY_ADDRESS);
			sender.setMargin(0);
			sender.setMarginBottom(4);
			sender.setFontSize(6);
			sender.setFixedLeading(8);

			Paragraph address = new Paragraph(customerName + "\n" + street + "\n" + city);
			address.setMargin(0);
			address.setFixedLeading(13);

			Cell addressCell = new Cell(4, 1);
			addressCell.setPadding(0);
			addressCell.setBorder(Border.NO_BORDER);
			addressCell.add(sender);
			addressCell.add(address);

			Table header = new Table(UnitValue.createPercentArray(new float[] { 58, 20, 22 }));
			header.useAllAvailableWidth();
			header.setFixedLayout();
			header.setMarginBottom(50);
			header.addCell(addressCell);
			header.addCell(createCell("Rechnung", TextAlignment.LEFT).setFont(boldFont));
			header.addCell(createCell(invoiceNumber, TextAlignment.RIGHT));
			header.addCell(createCell("Kunde", TextAlignment.LEFT).setFont(boldFont));
			header.addCell(createCell(customerNumber, TextAlignment.RIGHT));
			header.addCell(createCell("Datum", TextAlignment.LEFT).setFont(boldFont));
			header.addCell(createCell(date(invoice.get(INVOICE + "Invoice_Date__c")), TextAlignment.RIGHT));
			header.addCell(createCell("Fälligkeitsdatum", TextAlignment.LEFT).setFont(boldFont));
			header.addCell(createCell(dueDate, TextAlignment.RIGHT));
			document.add(header);

			Paragraph title = new Paragraph("Rechnung");
			title.setMargin(0);
			title.setMarginBottom(15);
			title.setFont(boldFont);
			title.setFontSize(14);
			title.setFixedLeading(18);
			document.add(title);

			Table items = new Table(UnitValue.createPercentArray(new float[] { 42, 9, 9, 15, 9, 16 }));
			items.useAllAvailableWidth();
			items.setFixedLayout();
			items.setMarginBottom(5);
			items.addHeaderCell(createHeaderCell("Bezeichnung / Leistungszeitraum", TextAlignment.LEFT, boldFont));
			items.addHeaderCell(createHeaderCell("Menge", TextAlignment.RIGHT, boldFont));
			items.addHeaderCell(createHeaderCell("Einheit", TextAlignment.LEFT, boldFont));
			items.addHeaderCell(createHeaderCell("Einzelpreis\n(brutto)", TextAlignment.RIGHT, boldFont));
			items.addHeaderCell(createHeaderCell("USt.", TextAlignment.RIGHT, boldFont));
			items.addHeaderCell(createHeaderCell("Gesamt\n(brutto)", TextAlignment.RIGHT, boldFont));

			for (Map<String, String> position : positions) {
				String description = position.get("Description__c");
				String startDate = date(position.get("Billing_Period_Start_Date__c"));
				String endDate = date(position.get("Billing_Period_End_Date__c"));
				String billingPeriod = startDate + " - " + endDate;

				String unit = position.get("Unit__c");
				if (unit.equals("Month")) {
					unit = "Monat";
				} else if (unit.equals("Piece")) {
					unit = "Stück";
				}

				String quantity = number(position.get("Quantity__c"), "#,##0");
				String unitPrice = number(position.get("Gross_Unit_Price__c"), AMOUNT_FORMAT);
				String taxRate = number(position.get("Tax_Rate__c"), "0") + " %";
				String amount = number(position.get("Gross_Amount__c"), AMOUNT_FORMAT);

				Paragraph period = new Paragraph(billingPeriod);
				period.setMargin(0);
				period.setMarginTop(3);
				period.setFontSize(8);
				period.setFixedLeading(10);

				Cell descriptionCell = createItemCell(description, TextAlignment.LEFT);
				descriptionCell.add(period);

				items.addCell(descriptionCell);
				items.addCell(createItemCell(quantity, TextAlignment.RIGHT));
				items.addCell(createItemCell(unit, TextAlignment.LEFT));
				items.addCell(createItemCell(unitPrice, TextAlignment.RIGHT));
				items.addCell(createItemCell(taxRate, TextAlignment.RIGHT));
				items.addCell(createItemCell(amount, TextAlignment.RIGHT));
			}
			document.add(items);

			String netAmount = number(invoice.get(INVOICE + "Total_Net_Amount__c"), AMOUNT_FORMAT) + " EUR";
			String taxAmount = number(invoice.get(INVOICE + "Total_Tax_Amount__c"), AMOUNT_FORMAT) + " EUR";
			String grossAmount = number(invoice.get(INVOICE + "Total_Gross_Amount__c"), AMOUNT_FORMAT) + " EUR";

			Cell grossLabel = createTotalCell("Rechnungsbetrag", TextAlignment.LEFT);
			grossLabel.setPaddingBottom(8);
			grossLabel.setFont(boldFont);

			Cell grossValue = createTotalCell(grossAmount, TextAlignment.RIGHT);
			grossValue.setPaddingBottom(8);
			grossValue.setFont(boldFont);

			Table totals = new Table(UnitValue.createPercentArray(new float[] { 70, 30 }));
			totals.useAllAvailableWidth();
			totals.setMarginBottom(25);
			totals.setBorderTop(new SolidBorder(0.5f));
			totals.setBorderBottom(new SolidBorder(0.5f));
			totals.addCell(createTotalCell("Summe netto", TextAlignment.LEFT));
			totals.addCell(createTotalCell(netAmount, TextAlignment.RIGHT));
			totals.addCell(createTotalCell("Umsatzsteuer (19 %)", TextAlignment.LEFT));
			totals.addCell(createTotalCell(taxAmount, TextAlignment.RIGHT));
			totals.addCell(grossLabel);
			totals.addCell(grossValue);

			String mandateActive = invoice.get(ACCOUNT + "Mandate_Active__c");
			String iban = invoice.get(ACCOUNT + "IBAN__c");
			String paymentText;

			if (mandateActive.equals("true")) {
				paymentText = "Wir ziehen den Rechnungsbetrag am " + dueDate + " von Ihrem Konto mit der IBAN "
						+ iban + " mit der Mandatsreferenz " + customerNumber
						+ " ein. Wir bitten Sie, für ausreichend Deckung auf Ihrem Konto zu sorgen.";
			} else {
				paymentText = "Bitte überweisen Sie den Rechnungsbetrag auf unser Konto mit der IBAN "
						+ "DE87 3106 0517 7000 8780 27. Damit Ihre Zahlung direkt zugeordnet werden kann, "
						+ "geben Sie bitte als Verwendungszweck ausschließlich Ihre Kundennummer an.";
			}

			Paragraph payment = new Paragraph(paymentText);
			payment.setMargin(0);
			payment.setMarginBottom(20);
			payment.setFixedLeading(13);

			Paragraph thanks = new Paragraph("Vielen Dank für die erfolgreiche Zusammenarbeit.");
			thanks.setMargin(0);
			thanks.setFont(boldFont);
			thanks.setFixedLeading(13);

			Div closing = new Div();
			closing.setKeepTogether(true);
			closing.add(totals);
			closing.add(payment);
			closing.add(thanks);
			document.add(closing);
			document.flush();

			int pageCount = pdf.getNumberOfPages();

			for (int page = 1; page <= pageCount; page++) {
				Paragraph footer = new Paragraph(FOOTER);
				footer.setMargin(0);
				footer.setFont(regularFont);
				footer.setFontSize(6);
				footer.setFixedLeading(8);
				footer.setTextAlignment(TextAlignment.CENTER);

				float pageWidth = pdf.getPage(page).getPageSize().getWidth();
				float centerX = pageWidth / 2;
				document.showTextAligned(footer, centerX, 70, page, TextAlignment.CENTER, VerticalAlignment.TOP, 0);

				Paragraph pageNumber = new Paragraph("Seite " + page + " von " + pageCount);
				pageNumber.setMargin(0);
				pageNumber.setFont(regularFont);
				pageNumber.setFontSize(8);
				pageNumber.setFixedLeading(10);

				float rightX = pageWidth - document.getRightMargin();
				document.showTextAligned(pageNumber, rightX, 24, page, TextAlignment.RIGHT, VerticalAlignment.TOP, 0);
			}
		}
	}

	private static Cell createCell(String text, TextAlignment alignment) {
		Paragraph paragraph = new Paragraph(text);
		paragraph.setMargin(0);
		paragraph.setFixedLeading(13);

		Cell cell = new Cell();
		cell.setPadding(0);
		cell.setBorder(Border.NO_BORDER);
		cell.setTextAlignment(alignment);
		cell.add(paragraph);

		return cell;
	}

	private static Cell createHeaderCell(String text, TextAlignment alignment, PdfFont boldFont) {
		Cell cell = createCell(text, alignment);
		cell.setMinHeight(31.2f);
		cell.setPaddings(0, 3, 3, 3);
		cell.setBorderBottom(new SolidBorder(0.5f));
		cell.setFont(boldFont);

		return cell;
	}

	private static Cell createItemCell(String text, TextAlignment alignment) {
		Cell cell = createCell(text, alignment);
		cell.setMinHeight(28.3f);
		cell.setKeepTogether(true);
		cell.setPaddings(8, 3, 4, 3);

		return cell;
	}

	private static Cell createTotalCell(String text, TextAlignment alignment) {
		Cell cell = createCell(text, alignment);
		cell.setPaddingTop(8);

		return cell;
	}

	private static String date(String value) {
		if (value.isEmpty()) {
			return "";
		}

		return LocalDate.parse(value).format(DATE);
	}

	private static String number(String value, String pattern) {
		return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.GERMANY))
				.format(new BigDecimal(value));
	}

}
