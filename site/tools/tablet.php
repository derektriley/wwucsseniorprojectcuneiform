<?php



class Tablet implements JsonSerializable {
    private $id;
    private $name;
    private $lang;
    private $objects;

    public function __construct($id, PDO $pdo) {
        $this->id  = $id;
        $sql       = "SELECT t.tablet_id, t.name, t.lang, o.tablet_object_id, o.obj_name, ts.text_section_id, l.text, ts.text_section_name\n" .
                     "FROM `tablet` t NATURAL JOIN `tablet_object` o NATURAL JOIN `text_section` ts NATURAL JOIN `line` l\n" .
                     "WHERE t.tablet_id = " . $id;
        try {
            $result = $pdo->query($sql)->fetchAll();
            if (empty($result)) {
                throw new Exception("Query returned no results", 0, NULL);
            }
        } catch (Exception $e) {
            echo 'Caught exception: ' . $e->getMessage() . "\n";
            echo $sql;
        }
        $this->name    = $result[0]['name'];
        $this->lang    = $result[0]['lang'];
        $this->objects = $this->buildObjects($result);
    }

    private function buildObjects($result) {
        $outputObjects = array();
        $object = NULL;
        $section = NULL;
        foreach ($result as $row) {
            if ($object == NULL) {
                $object = new TabletObject($row['tablet_object_id'], $row['obj_name']);
            }
            if ($section == NULL) {
                $section = new TextSection($row['text_section_id'], $row['text_section_name']);
            }

            if ($section->getID() == $row['text_section_id']) {
                $section->addLine($row['text']); // Same section, add line to it.
            } else {
                // New section, add current section to current object
                $object->addSection($section);
                // Create new TextSection
                $section = new TextSection($row['text_section_id'],  $row['text_section_name']);
                // Add line to it
                $section->addLine($row['text']);
            }

            if ($object->getID() != $row['tablet_object_id']) {
                // New object, add current object to outputObjects
                array_push($outputObjects, $object);
                // Create new TabletObject
                $object = new TabletObject($row['tablet_object_id'], $row['obj_name']);
            }
        }
        $object->addSection($section);
        array_push($outputObjects, $object);
        return $outputObjects;
    }

    public function display() {
        echo "<div class=\"panel panel-default\">\n" .
             "<div class = \"panel-heading\"><a href=\"http://www.cdli.ucla.edu/search/search_results.php?SearchMode=Text&ObjectID=" . substr($this->name, 1,7) . "&requestFrom=Submit+Query\">$this->name</a></div>\n" .
             "<div class = \"panel-body\">";

        foreach ($this->objects as $object) {
            $object->display();
        }

        echo "</div></div>";
    }

    public function jsonSerialize() {
        return [
                   'id'      => $this->id,
                   'name'    => $this->name,
                   'lang'    => $this->lang,
                   'objects' => $this->objects
               ];
    }

}

class TabletObject implements JsonSerializable{
    private $id;
    private $name;
    private $sections;

    public function __construct($id, $name) {
        $this->id       = $id;
        $this->name     = $name;
        $this->sections = array();
    }

    public function getID() {
        return $this->id;
    }

    public function addSection($section) {
        array_push($this->sections, $section);
    }

    public function display() {
        echo "<div class=\"panel panel-default\">\n",
             "<div class = \"panel-heading\"><span class=\"expand-text\">", $this->name, "</span></div>\n",
             "<div class = \"panel-body\">";
        foreach ($this->sections as $section) {
            $section->display();
        }
        echo "</div></div>";
    }

    public function jsonSerialize() {
        return [
                   'id'       => $this->id,
                   'name'     => $this->name,
                   'sections' => $this->sections
               ];
    }

}

class TextSection implements JsonSerializable{
    private $id;
    private $name;
    private $lines;

    public function __construct($id, $name) {
        $this->id    = $id;
        $this->name  = $name;
        $this->lines = array();
    }

    public function getID() {
        return $this->id;
    }

    public function addLine($line) {
        array_push($this->lines, $line);
    }

    public function insertmarks($line){
    	global $termlist;
	$pieces = explode(" ", htmlspecialchars($line));
	for($x=0; $x< sizeof($pieces); $x++){
		  if(in_array($pieces[$x], $termlist)){
			$pieces[$x] = "<mark>".$pieces[$x]."</mark>";
		  }
   	}
	return implode(" ", $pieces);
    }

    public function display() {
    	   
	
        echo "<div class=\"panel panel-default\">\n" .
             "<div class = \"panel-heading\">" . $this->name . "</div>\n" .
             "<div class = \"panel-body\">\n";
        echo "<ol>\n";
        foreach ($this->lines as $line) {
	    $line = $this->insertmarks($line);	         
            echo "<li>", $line, "</li>";
        }
        echo "</ol>";
        echo "</div></div>";
    }

    public function jsonSerialize() {
        return [
                   'id'    => $this->id,
                   'name'  => $this->name,
                   'lines' => $this->lines
               ];
    }

}

?>