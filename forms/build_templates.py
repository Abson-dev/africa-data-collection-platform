"""Builds the XLSForm templates in forms/templates/.

The .xlsx files are generated from this script so changes can be reviewed as code.
Edit here, then run:   python forms/build_templates.py
Check with pyxform:    xls2xform forms/templates/<name>.xlsx out.xml

Requires: openpyxl
"""

from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Font

OUT = Path(__file__).parent / "templates"
VERSION = "2026110901"
EN, FR = "English (en)", "Français (fr)"
SMS_NAMESPACE = 'adc="https://github.com/Abson-dev/africa-data-collection-platform/xforms"'

YES_NO = [
    {"list_name": "yes_no", "name": "yes", f"label::{EN}": "Yes", f"label::{FR}": "Oui"},
    {"list_name": "yes_no", "name": "no", f"label::{EN}": "No", f"label::{FR}": "Non"},
]

# Standard metadata recorded on every form.
META = [
    {"type": "start", "name": "start"},
    {"type": "end", "name": "end"},
    {"type": "today", "name": "today"},
    {"type": "deviceid", "name": "deviceid"},
]

CONSENT = [
    {
        "type": "note",
        "name": "intro",
        f"label::{EN}": "This survey is run by [ORGANISATION] to [PURPOSE]. Your answers are confidential "
        "and stored securely. Taking part is voluntary and you can stop at any time.",
        f"label::{FR}": "Cette enquête est menée par [ORGANISATION] pour [OBJECTIF]. Vos réponses sont "
        "confidentielles et stockées de manière sécurisée. La participation est volontaire et vous "
        "pouvez arrêter à tout moment.",
    },
    {
        "type": "select_one yes_no",
        "name": "consent",
        f"label::{EN}": "Do you agree to take part?",
        f"label::{FR}": "Acceptez-vous de participer ?",
        "required": "yes",
    },
    {
        "type": "note",
        "name": "no_consent",
        f"label::{EN}": "Thank the respondent, end the interview and save the form.",
        f"label::{FR}": "Remerciez le répondant, terminez l'entretien et enregistrez le formulaire.",
        "relevant": "${consent} = 'no'",
    },
]


def write_xlsform(name, survey, choices, settings):
    """Writes one XLSForm with survey, choices and settings sheets."""
    wb = Workbook()
    for sheet_name, rows in (("survey", survey), ("choices", choices), ("settings", [settings])):
        ws = wb.active if sheet_name == "survey" else wb.create_sheet(sheet_name)
        ws.title = sheet_name
        columns = list(dict.fromkeys(key for row in rows for key in row))
        ws.append(columns)
        for cell in ws[1]:
            cell.font = Font(bold=True)
        for row in rows:
            ws.append([row.get(c, "") for c in columns])
        for i, column in enumerate(columns, start=1):
            ws.column_dimensions[ws.cell(1, i).column_letter].width = max(12, min(60, len(column) + 4))
        ws.freeze_panes = "A2"
    OUT.mkdir(exist_ok=True)
    wb.save(OUT / f"{name}.xlsx")
    print(f"wrote {OUT / name}.xlsx")


def consent_template():
    """Starting point for any form that collects personal data (Phase 1 spec, section 6)."""
    survey = META + CONSENT + [
        {"type": "begin_group", "name": "main", f"label::{EN}": "Survey", f"label::{FR}": "Enquête",
         "relevant": "${consent} = 'yes'"},
        {"type": "text", "name": "example_question",
         f"label::{EN}": "Replace this with your questions", f"label::{FR}": "Remplacez ceci par vos questions"},
        {"type": "end_group", "name": "main"},
    ]
    settings = {"form_title": "Consent template", "form_id": "consent_template", "version": VERSION,
                "default_language": EN}
    write_xlsform("consent", survey, YES_NO, settings)


def sms_partial():
    """Server-side form: the Bridge writes one submission per SMS fallback (PartialXml.kt)."""
    survey = [
        {"type": "text", "name": "source_form_id", f"label::{EN}": "Original form ID",
         f"label::{FR}": "ID du formulaire d'origine"},
        {"type": "text", "name": "source_form_version", f"label::{EN}": "Original form version",
         f"label::{FR}": "Version du formulaire d'origine"},
        {"type": "text", "name": "source_instance_id", f"label::{EN}": "Original record ID",
         f"label::{FR}": "ID de l'enregistrement d'origine"},
        {"type": "text", "name": "app_user", f"label::{EN}": "Enumerator (app user)",
         f"label::{FR}": "Enquêteur (utilisateur de l'application)"},
        {"type": "dateTime", "name": "received_at", f"label::{EN}": "SMS received at",
         f"label::{FR}": "SMS reçu le"},
        {"type": "text", "name": "payload_json", f"label::{EN}": "Fields sent by SMS (JSON)",
         f"label::{FR}": "Champs envoyés par SMS (JSON)"},
    ]
    settings = {
        "form_title": "SMS partial records",
        "form_id": "sms_partial",
        "version": VERSION,
        "default_language": EN,
        "instance_name": "concat('SMS ', ${source_form_id}, ' - ', ${app_user})",
    }
    write_xlsform("sms_partial", survey, [], settings)


def example_household_sms():
    """Shows how to mark fields for SMS fallback with the bind::adc:sms column."""
    sms = "bind::adc:sms"
    survey = META + CONSENT + [
        {"type": "begin_group", "name": "main", f"label::{EN}": "Household", f"label::{FR}": "Ménage",
         "relevant": "${consent} = 'yes'"},
        {"type": "text", "name": "district", f"label::{EN}": "District", f"label::{FR}": "District",
         "required": "yes", sms: "yes"},
        {"type": "text", "name": "village", f"label::{EN}": "Village or neighbourhood",
         f"label::{FR}": "Village ou quartier", "required": "yes", sms: "yes"},
        {"type": "text", "name": "head_name", f"label::{EN}": "Name of household head",
         f"label::{FR}": "Nom du chef de ménage", sms: "yes"},
        {"type": "integer", "name": "members", f"label::{EN}": "Number of household members",
         f"label::{FR}": "Nombre de membres du ménage", "constraint": ". >= 1 and . <= 50", sms: "yes"},
        {"type": "select_one needs", "name": "urgent_need", f"label::{EN}": "Most urgent need",
         f"label::{FR}": "Besoin le plus urgent", sms: "yes"},
        {"type": "geopoint", "name": "gps", f"label::{EN}": "Household location",
         f"label::{FR}": "Position du ménage", sms: "yes"},
        {"type": "image", "name": "photo", f"label::{EN}": "Photo of the dwelling (not sent by SMS)",
         f"label::{FR}": "Photo du logement (non envoyée par SMS)", "parameters": "max-pixels=1024"},
        {"type": "text", "name": "notes", f"label::{EN}": "Notes", f"label::{FR}": "Notes",
         "appearance": "multiline"},
        {"type": "end_group", "name": "main"},
    ]
    needs = [
        {"list_name": "needs", "name": n, f"label::{EN}": en, f"label::{FR}": fr}
        for n, en, fr in [
            ("water", "Water", "Eau"),
            ("food", "Food", "Nourriture"),
            ("health", "Health care", "Soins de santé"),
            ("shelter", "Shelter", "Abri"),
            ("other", "Other", "Autre"),
        ]
    ]
    settings = {
        "form_title": "Example: household survey with SMS fallback",
        "form_id": "example_household_sms",
        "version": VERSION,
        "default_language": EN,
        "namespaces": SMS_NAMESPACE,
    }
    write_xlsform("example_household_sms", survey, YES_NO + needs, settings)


if __name__ == "__main__":
    consent_template()
    sms_partial()
    example_household_sms()
