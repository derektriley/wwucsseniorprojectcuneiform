package cuneiform;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.sql.*;

public class Tablet
        implements Comparable<Tablet> {
    private int                      id;
    public final String              name;
    public final String              lang;
    public final List<TabletObject>  objects;
    public FoundDate                 foundMonth;
    public FoundDate                 foundYear;
    public final List<String>        names = new ArrayList<>();

    Tablet(String name, String lang, String object, List<TabletObject> objects)
            throws IOException {
        this.id      = 0;
        this.name    = name;
        this.lang    = lang;
        this.objects = objects;
        assert (name.charAt(0) == '&');
    }

    public void print(PrintStream output) {
        output.println(name);
        output.println(lang);
        for (TabletObject t : objects) {
            t.print(output);
        }
    }

    public void insert(Connection conn) {
        try {
            this.insertTabletRecord(conn);

            for (TabletObject object : this.objects) {
                object.insert(conn, this.id);
            }
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void insertTabletRecord(Connection conn)
            throws SQLException
    {
        String query = "INSERT INTO `tablet` (`name`, `lang`) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            // TODO: Find way to insert default value.
            stmt.setString(2, (lang == null) ? "sux" : lang);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if ((rs != null) && (rs.next())) {
                    this.id = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw e;
        }
    }

    public void setMonth(FoundDate newMonth) {
        if (foundMonth == null || newMonth.compareTo(foundMonth) > 0) {
            foundMonth = newMonth;
        }
    }

    public void setYear(FoundDate newYear) {
        if (foundYear == null || newYear.compareTo(foundYear) > 0) {
            foundYear = newYear;
        }
    }

    public void addName(String name) {
        names.add(name);
    }

    public void printStats(PrintStream output) {
        output.format("%-27s %s%n", "name:", name);
        output.println(" month data:");
        if (foundMonth != null) {
            foundMonth.printStats(output);

        }
        output.println(" year data:");
        if (foundYear != null) {
            foundYear.printStats(output);
        }
        output.println(" names:");
        for (String n : names) {
            output.format("  %s%n", n);
        }
        output.format("%n");
    }

    @Override
    public int compareTo(Tablet othe) {
        int rv = compare(this.foundYear, othe.foundYear);
        if(rv != 0) return rv;
        return compare(this.foundMonth, othe.foundMonth);
        /*
        double thisC = ((this.foundMonth == null) ? (0) : (this.foundMonth.confidence.confidence)) + ((this.foundYear == null) ? (0) : (this.foundYear.confidence.confidence));
        double otheC = ((othe.foundMonth == null) ? (0) : (othe.foundMonth.confidence.confidence)) + ((othe.foundYear == null) ? (0) : (othe.foundYear.confidence.confidence));
        int rv1 = Double.compare(otheC, thisC);
        if (rv1 != 0) return rv1;
        int rv2 = Integer.compare(othe.names.size(), this.names.size());
        if (rv2 != 0) return rv2;
        return othe.name.compareTo(this.name);
        */
    }

    private static int compare(FoundDate fd1, FoundDate fd2) {
        if(fd1 == null && fd2 == null) return 0;
        if(fd1 == null) return 1;
        if(fd2 == null) return -1;
        return -fd1.confidence.compareTo(fd2.confidence);
    }
}

class TabletObject {
    private      int                 id;
    public final String              name;
    public final List<TabletSection> sections;
    public TabletObject(String name, List<TabletSection> sections) {
        this.id       = 0;
        this.name     = name;
        this.sections = sections;
        assert(this.name.charAt(0) == '@');
    }

    public void print(PrintStream output) {
        output.println(name);
        for(TabletSection s : sections) {
            s.print(output);
        }
    }

    public void insert(Connection conn, int tabletID)
            throws SQLException {
        insertTabletObject(conn, tabletID);

        for (TabletSection section : sections) {
            section.insert(conn, this.id);
        }
    }

    private void insertTabletObject(Connection conn, int tabletID)
            throws SQLException {
        String query = "INSERT INTO `tablet_object` (`tablet_id`, `obj_name`) "
                     + "VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, tabletID); // Parameters indices are 1-based
            stmt.setString(2, name);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if ((rs != null) && (rs.next())) {
                    this.id = rs.getInt(1);
                }
            }
        }
    }
}

class TabletSection {
	private int id;
	
    public final String       title;
    public final List<String> lines;

    TabletSection(String title, List<String> lines) {
        this.id    = 0;
        this.title = title;
        this.lines = lines;
        assert(title.charAt(0) == '@');
    }

    public void print(PrintStream output) {
        output.println(title);
        for (int i = 0; i < lines.size(); ++i) {
            output.format("%3d. %s%n", i + 1, lines.get(i));
        }
    }

    public void insert(Connection conn, int objectID)
            throws SQLException {
        int tabletSectionID = this.insertTabletSection(conn, objectID);

        try (Statement stmt = conn.createStatement()) {
            for (String line : this.lines) {
                this.insertLine(stmt, tabletSectionID, line);
            }
        }
    }

    private int insertTabletSection(Connection conn, int tabletObjectId)
            throws SQLException {
        String query = "INSERT INTO `text_section` "
                     + "(`tablet_object_id`, `text_section_type_id`, `section_text`) "
                     + "VALUES (?, ?, ?)";
        String text = "";
        for (String line : this.lines) {
            text += line + " ";
        }
        Integer sectionType = this.getSectionType();
        try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            // Indices are 1-based
            stmt.setInt(1, tabletObjectId);
            stmt.setString(3, text);

            if (sectionType == null) {
                stmt.setNull(2, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(2, sectionType);
            }

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if ((rs != null) && (rs.next())) {
                    this.id = rs.getInt(1);
                }
            }
            return this.id;
        }
    }

    private void insertLine(Statement stmt, int tabletSectionID, String line)
    		throws SQLException
    {
    	StringBuilder sb = new StringBuilder();
    	
    	sb.append("INSERT INTO `line` ");
        sb.append("(`text_section_id`, `text`) VALUES ");
    	sb.append
    	(
    		String.format
    		(
    			" (%d, '%s');",
    			tabletSectionID,
    			line.replace("'",  "\\'")
    		)
    	);
    	
    	stmt.execute(sb.toString());
    }

    public void insertMonth(Connection conn, String text, FoundDate date)
    {
        try (Statement stmt = conn.createStatement()) {
            StringBuilder sb = new StringBuilder();

            sb.append("INSERT INTO `month_reference` ");
            sb.append("(`text_section_id`, `canonical_month_id`, `text`, `confidence`) VALUES ");
	    	sb.append
	    	(
	    		String.format
	    		(
		    		" (%d, %d, '%s', %.3f);",
		    		this.id,
		    		date.getKnownDate().id,
		    		text.replace("'",  "\\'"),
		    		date.confidence.confidence / 100
	    		)
	    	);
	    	
	    	stmt.execute(sb.toString());
		}
		catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    public void insertYear(Connection conn, String text, FoundDate date)
    {
        try (Statement stmt = conn.createStatement()) {
            StringBuilder sb = new StringBuilder();
			
            sb.append("INSERT INTO `year_reference` ");
            sb.append("(`text_section_id`, `canonical_year_id`, `text`, `confidence`) VALUES ");
	    	sb.append
	    	(
	    		String.format
	    		(
	    			" (%d, %d, '%s', %.3f);",
	    			this.id,
	    			date.getKnownDate().id,
	    			text.replace("'",  "\\'"),
	    			date.confidence.confidence / 100
	    		)
	    	);
	    	
	    	stmt.execute(sb.toString());
		}
		catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    private Integer getSectionType() {
    	
    	if (this.title.startsWith("@seal")) {
    		return 9;
    	}
    	
    	switch (this.title.trim()) {
    		case "@bottom":
    			return 1;
    			
    		case "@bulla":
    			return 2;
    			
    		case "@edge":
    			return 3;
    			
    		case "@envelope":
    			return 4;
    			
    		case "@left":
    			return 5;
    			
    		case "@object":
    			return 6;
    			
    		case "@obverse":
    			return 7;
    			
    		case "@reverse":
    			return 8;
    			
    		case "@seal":
    			return 9;
    			
    		case "@tablet":
    			return 10;
    			
    		case "@top":
    			return 11;
    			
    		default:
    			return null;
    	}
    }
}
