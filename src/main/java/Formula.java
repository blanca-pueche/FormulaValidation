import exceptions.IncorrectFormula;
import exceptions.NotFoundElement;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Formula {
    private static final double ELECTRON_WEIGHT = 0.00054858;
    private static final String CC_URL = "https://www.chemcalc.org/chemcalc/mf";
    private static final int DEFAULT_PPM = 50;

    private Map<String, Integer> elements;
    private String adduct;
    private int charge;
    private String chargeType;
    private double monoisotopicMass;
    private double monoisotopicMassWithAdduct;
    private Map<String, Object> metadata;

    public Formula(Map<String, Integer> elements, String adduct, int charge, String chargeType, Map<String, Object> metadata) throws IncorrectFormula, NotFoundElement {
        this.metadata = metadata != null ? metadata : new HashMap<>();

        this.elements = new HashMap<>();
        for (Map.Entry<String, Integer> entry : elements.entrySet()) {
            if (entry.getValue() <= 0) {
                throw new IncorrectFormula(elements);
            }
            this.elements.put(entry.getKey(), this.elements.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }

        this.charge = charge;
        if (chargeType.equals("") || chargeType.equals("+") || chargeType.equals("-")) {
            this.chargeType = chargeType;
        } else {
            throw new IncorrectFormula("charge_type " + chargeType + " invalid. It should be +, - or empty");
        }

        if (adduct == null || adduct.equals("None") || adduct.equals("")) {
            this.adduct = null;
        } else {
            this.adduct = adduct;
        }

        this.monoisotopicMass = calculateMonoisotopicMass();
        this.monoisotopicMassWithAdduct = calculateMonoisotopicMassWithAdduct();
    }
    public Formula(Map<String, Integer> elements, String adduct, int charge, String chargeType) throws IncorrectFormula {
        this.elements = new HashMap<>();
        for (Map.Entry<String, Integer> entry : elements.entrySet()) {
            if (entry.getValue() <= 0) {
                throw new IncorrectFormula(elements);
            }
            this.elements.put(entry.getKey(), this.elements.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }

        this.charge = charge;
        if (chargeType.equals("") || chargeType.equals("+") || chargeType.equals("-")) {
            this.chargeType = chargeType;
        } else {
            throw new IncorrectFormula("charge_type " + chargeType + " invalid. It should be +, - or empty");
        }

        if (adduct == null || adduct.equals("None") || adduct.equals("")) {
            this.adduct = null;
        } else {
            this.adduct = adduct;
        }

        this.monoisotopicMass = calculateMonoisotopicMass();
        this.monoisotopicMassWithAdduct = calculateMonoisotopicMassWithAdduct();
    }

    public boolean equals(Object other) {
        if (other instanceof Formula) {
            Formula otherFormula = (Formula) other;
            return this.elements.equals(otherFormula.getElements()) && this.adduct.equals(otherFormula.adduct);
        }
        return false;
    }

    public String toString() {
        StringBuilder formulaString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : this.elements.entrySet()) {
            formulaString.append(entry.getKey()).append(entry.getValue() > 1 ? entry.getValue() : "");
        }
        if (!this.chargeType.equals("")) {
            String chargeStr = this.charge == 1 ? "" : String.valueOf(this.charge);
            formulaString.append(this.chargeType).append(chargeStr);
        }
        String adductStr = this.adduct == null ? "" : "+" + this.adduct;
        return formulaString.append(adductStr).toString();
    }

    public String getFinalFormulaWithAdduct() throws IncorrectFormula {
        if (this.adduct == null) {
            return toString();
        }
        Formula finalFormula = this.multiply(Integer.parseInt(this.adduct.replaceAll("\\D", "")));
        // Assuming the adduct processing logic to be added here
        return finalFormula.toString();
    }

    public int hashCode() {
        return this.elements.hashCode();
    }

    public Formula add(Formula other) throws IncorrectFormula {
        Map<String, Integer> newElements = new HashMap<>(this.elements);
        for (Map.Entry<String, Integer> entry : other.elements.entrySet()) {
            newElements.put(entry.getKey(), newElements.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }
        int newCharge = this.chargeType.equals("-") ? -this.charge : this.charge;
        newCharge = other.chargeType.equals("-") ? newCharge - other.charge : newCharge + other.charge;
        String newChargeType = newCharge == 0 ? "" : (newCharge > 0 ? "+" : "-");
        return new Formula(newElements, null, Math.abs(newCharge), newChargeType);
    }

    public Formula subtract(Formula other) throws IncorrectFormula {
        Map<String, Integer> newElements = new HashMap<>(this.elements);
        for (Map.Entry<String, Integer> entry : other.elements.entrySet()) {
            newElements.put(entry.getKey(), newElements.getOrDefault(entry.getKey(), 0) - entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : newElements.entrySet()) {
            if (entry.getValue() < 0) {
                throw new IncorrectFormula("The subtraction of these two formulas contains a negative number of " + entry.getKey());
            }
        }
        int newCharge = this.chargeType.equals("-") ? -this.charge : this.charge;
        newCharge = other.chargeType.equals("-") ? newCharge + other.charge : newCharge - other.charge;
        String newChargeType = newCharge == 0 ? "" : (newCharge > 0 ? "+" : "-");
        return new Formula(newElements, null, Math.abs(newCharge), newChargeType);
    }

    public Formula multiply(int numToMultiply) throws IncorrectFormula {
        Map<String, Integer> newElements = new HashMap<>(this.elements);
        for (Map.Entry<String, Integer> entry : newElements.entrySet()) {
            newElements.put(entry.getKey(), entry.getValue() * numToMultiply);
        }
        return new Formula(newElements, this.adduct, this.charge, this.chargeType);
    }

    public static Formula formulaFromStringHill(String formulaStr, String adduct, Map<String, Object> metadata) throws IncorrectFormula, NotFoundElement {
        if (!formulaStr.matches("^[\\[?a-zA-Z0-9\\]?]+(\\(?[+-]?\\d*\\)?)?$")) {
            throw new IncorrectFormula(formulaStr);
        }

        Pattern pattern = Pattern.compile("(\\[\\d+\\])?([A-Z][a-z]*)(\\d*)");
        Matcher matcher = pattern.matcher(formulaStr);
        Map<String, Integer> elements = new HashMap<>();

        while (matcher.find()) {
            String element = matcher.group(2);
            int appearances = matcher.group(3).isEmpty() ? 1 : Integer.parseInt(matcher.group(3));
            elements.put(element, elements.getOrDefault(element, 0) + appearances);
        }

        Pattern chargePattern = Pattern.compile("\\(?([-+]?\\d*)\\)?$");
        Matcher chargeMatcher = chargePattern.matcher(formulaStr);
        int charge = 0;
        String chargeType = "";

        if (chargeMatcher.find()) {
            chargeType = chargeMatcher.group(1).substring(0, 1);
            String chargeValue = chargeMatcher.group(1).substring(1);
            charge = chargeValue.isEmpty() ? 1 : Integer.parseInt(chargeValue);
        }

        return new Formula(elements, adduct, charge, chargeType, metadata);
    }

    private double calculateMonoisotopicMass() {
        // Implement the logic for calculating monoisotopic mass
        return 0.0;
    }

    private double calculateMonoisotopicMassWithAdduct() {
        // Implement the logic for calculating monoisotopic mass with adduct
        return 0.0;
    }

    public Map<String, Integer> getElements() {
        return new HashMap<>(this.elements);
    }

    public static void main(String[] args) {
        try {
            Map<String, Integer> elements = new HashMap<>();
            elements.put("C", 4);
            elements.put("H", 5);
            elements.put("N", 6);
            elements.put("Na", 1);

            Formula formula = new Formula(elements, "[M+H]+", 1, "+", null);
            System.out.println(formula);
        } catch (IncorrectFormula | NotFoundElement e) {
            e.printStackTrace();
        }
    }
}
