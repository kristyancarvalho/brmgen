import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")

from gi.repository import Gtk, Adw, Gio, GLib
from pathlib import Path

from .backend import (
    validate_model,
    build_model,
    run_doctor,
    get_version,
)


class MainWindow(Gtk.ApplicationWindow):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.set_default_size(900, 650)
        self.set_title("brmgen GTK")

        self.input_file = None
        self.output_file = None
        self.brmodelo_jar = None

        self._build_ui()

    def _build_ui(self):
        header = Adw.HeaderBar()
        self.set_titlebar(header)

        main_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        self.set_child(main_box)

        toolbar = Adw.ToolbarView()
        main_box.append(toolbar)

        content = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        content.set_margin_top(12)
        content.set_margin_bottom(12)
        content.set_margin_start(12)
        content.set_margin_end(12)
        toolbar.set_content(content)

        self._build_file_section(content)
        self._build_options_section(content)
        self._build_action_buttons(content)
        self._build_output_section(content)

    def _build_file_section(self, parent):
        group = Adw.PreferencesGroup(title="Arquivos")
        parent.append(group)

        input_row = Adw.ActionRow(title="Arquivo de entrada (YAML/JSON)")
        self.input_button = Gtk.Button(label="Selecionar...")
        self.input_button.connect("clicked", self._on_select_input)
        input_row.add_suffix(self.input_button)
        input_row.set_activatable_widget(self.input_button)
        group.add(input_row)

        output_row = Adw.ActionRow(title="Arquivo de saída (.brM3)")
        self.output_button = Gtk.Button(label="Selecionar...")
        self.output_button.connect("clicked", self._on_select_output)
        output_row.add_suffix(self.output_button)
        output_row.set_activatable_widget(self.output_button)
        group.add(output_row)

        jar_row = Adw.ActionRow(title="brModelo JAR (opcional)")
        self.jar_button = Gtk.Button(label="Selecionar...")
        self.jar_button.connect("clicked", self._on_select_jar)
        jar_row.add_suffix(self.jar_button)
        jar_row.set_activatable_widget(self.jar_button)
        group.add(jar_row)

        self.input_label = Gtk.Label(label="Nenhum arquivo selecionado")
        self.input_label.set_xalign(0)
        self.input_label.set_margin_start(12)
        self.input_label.set_margin_end(12)
        self.input_label.set_margin_bottom(6)
        parent.append(self.input_label)

        self.output_label = Gtk.Label(label="Padrão: ao lado do arquivo de entrada")
        self.output_label.set_xalign(0)
        self.output_label.set_margin_start(12)
        self.output_label.set_margin_end(12)
        self.output_label.set_margin_bottom(6)
        parent.append(self.output_label)

        self.jar_label = Gtk.Label(label="Usa BRMODELO_JAR do ambiente ou --brmodelo-jar")
        self.jar_label.set_xalign(0)
        self.jar_label.set_margin_start(12)
        self.jar_label.set_margin_end(12)
        self.jar_label.set_margin_bottom(6)
        parent.append(self.jar_label)

    def _build_options_section(self, parent):
        group = Adw.PreferencesGroup(title="Opções")
        parent.append(group)

        self.logical_switch = Gtk.Switch()
        self.logical_switch.set_valign(Gtk.Align.CENTER)
        logical_row = Adw.ActionRow(title="Gerar modelo lógico", subtitle="Transforma conceitual para lógico")
        logical_row.add_suffix(self.logical_switch)
        group.add(logical_row)

        self.force_switch = Gtk.Switch()
        self.force_switch.set_valign(Gtk.Align.CENTER)
        force_row = Adw.ActionRow(title="Sobrescrever saída", subtitle="Força sobrescrita se arquivo existir")
        force_row.add_suffix(self.force_switch)
        group.add(force_row)

    def _build_action_buttons(self, parent):
        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        box.set_margin_top(6)
        box.set_halign(Gtk.Align.CENTER)
        parent.append(box)

        self.validate_btn = Gtk.Button(label="Validar")
        self.validate_btn.add_css_class("suggested-action")
        self.validate_btn.connect("clicked", self._on_validate)
        box.append(self.validate_btn)

        self.build_btn = Gtk.Button(label="Gerar .brM3")
        self.build_btn.add_css_class("suggested-action")
        self.build_btn.connect("clicked", self._on_build)
        box.append(self.build_btn)

        self.doctor_btn = Gtk.Button(label="Doctor")
        self.doctor_btn.connect("clicked", self._on_doctor)
        box.append(self.doctor_btn)

    def _build_output_section(self, parent):
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.AUTOMATIC, Gtk.PolicyType.AUTOMATIC)
        scrolled.set_vexpand(True)
        scrolled.set_min_content_height(200)
        parent.append(scrolled)

        self.output_text = Gtk.TextView()
        self.output_text.set_editable(False)
        self.output_text.set_monospace(True)
        self.output_text.set_top_margin(6)
        self.output_text.set_bottom_margin(6)
        self.output_text.set_left_margin(6)
        self.output_text.set_right_margin(6)
        scrolled.set_child(self.output_text)

        self.output_buffer = self.output_text.get_buffer()

    def _on_select_input(self, button):
        dialog = Gtk.FileDialog(title="Selecionar arquivo de entrada")
        filters = Gio.ListStore.new(Gtk.FileFilter)
        yaml_filter = Gtk.FileFilter()
        yaml_filter.set_name("YAML/JSON")
        yaml_filter.add_pattern("*.yaml")
        yaml_filter.add_pattern("*.yml")
        yaml_filter.add_pattern("*.json")
        filters.append(yaml_filter)
        all_filter = Gtk.FileFilter()
        all_filter.set_name("Todos os arquivos")
        all_filter.add_pattern("*")
        filters.append(all_filter)
        dialog.set_filters(filters)
        dialog.open(self, None, self._on_input_selected)

    def _on_input_selected(self, dialog, result):
        try:
            file = dialog.open_finish(result)
            if file:
                self.input_file = Path(file.get_path())
                self.input_label.set_text(str(self.input_file))
                if not self.output_file:
                    default_output = self.input_file.with_suffix(".brM3")
                    self.output_label.set_text(f"Padrão: {default_output}")
        except GLib.Error:
            pass

    def _on_select_output(self, button):
        dialog = Gtk.FileDialog(title="Selecionar arquivo de saída")
        filters = Gio.ListStore.new(Gtk.FileFilter)
        brm3_filter = Gtk.FileFilter()
        brm3_filter.set_name("brModelo (.brM3)")
        brm3_filter.add_pattern("*.brM3")
        filters.append(brm3_filter)
        all_filter = Gtk.FileFilter()
        all_filter.set_name("Todos os arquivos")
        all_filter.add_pattern("*")
        filters.append(all_filter)
        dialog.set_filters(filters)
        dialog.save(self, None, self._on_output_selected)

    def _on_output_selected(self, dialog, result):
        try:
            file = dialog.save_finish(result)
            if file:
                self.output_file = Path(file.get_path())
                self.output_label.set_text(str(self.output_file))
        except GLib.Error:
            pass

    def _on_select_jar(self, button):
        dialog = Gtk.FileDialog(title="Selecionar brModelo JAR")
        filters = Gio.ListStore.new(Gtk.FileFilter)
        jar_filter = Gtk.FileFilter()
        jar_filter.set_name("Java Archive")
        jar_filter.add_pattern("*.jar")
        filters.append(jar_filter)
        all_filter = Gtk.FileFilter()
        all_filter.set_name("Todos os arquivos")
        all_filter.add_pattern("*")
        filters.append(all_filter)
        dialog.set_filters(filters)
        dialog.open(self, None, self._on_jar_selected)

    def _on_jar_selected(self, dialog, result):
        try:
            file = dialog.open_finish(result)
            if file:
                self.brmodelo_jar = Path(file.get_path())
                self.jar_label.set_text(str(self.brmodelo_jar))
        except GLib.Error:
            pass

    def _append_output(self, text: str):
        end_iter = self.output_buffer.get_end_iter()
        self.output_buffer.insert(end_iter, text + "\n")
        mark = self.output_buffer.create_mark(None, end_iter, False)
        self.output_text.scroll_mark_onscreen(mark)

    def _clear_output(self):
        self.output_buffer.set_text("")

    def _set_buttons_sensitive(self, sensitive: bool):
        self.validate_btn.set_sensitive(sensitive)
        self.build_btn.set_sensitive(sensitive)
        self.doctor_btn.set_sensitive(sensitive)

    def _on_validate(self, button):
        if not self.input_file:
            self._show_error("Selecione um arquivo de entrada")
            return

        self._clear_output()
        self._append_output(f"Validando: {self.input_file}")
        self._set_buttons_sensitive(False)

        def task():
            success, output = validate_model(self.input_file)
            GLib.idle_add(self._on_validate_done, success, output)

        import threading
        threading.Thread(target=task, daemon=True).start()

    def _on_validate_done(self, success: bool, output: str):
        self._append_output(output)
        if success:
            self._append_output("\n✓ Modelo válido")
        else:
            self._append_output("\n✗ Modelo inválido")
        self._set_buttons_sensitive(True)

    def _on_build(self, button):
        if not self.input_file:
            self._show_error("Selecione um arquivo de entrada")
            return

        self._clear_output()
        self._append_output(f"Gerando: {self.input_file}")
        self._set_buttons_sensitive(False)

        def task():
            output_path = self.output_file if self.output_file else None
            success, output = build_model(
                self.input_file,
                output_path,
                self.logical_switch.get_active(),
                self.brmodelo_jar,
                self.force_switch.get_active(),
            )
            GLib.idle_add(self._on_build_done, success, output)

        import threading
        threading.Thread(target=task, daemon=True).start()

    def _on_build_done(self, success: bool, output: str):
        self._append_output(output)
        if success:
            self._append_output("\n✓ Arquivo .brM3 gerado com sucesso")
        else:
            self._append_output("\n✗ Falha ao gerar arquivo")
        self._set_buttons_sensitive(True)

    def _on_doctor(self, button):
        self._clear_output()
        self._append_output("Verificando compatibilidade...")
        self._set_buttons_sensitive(False)

        def task():
            success, output = run_doctor(self.brmodelo_jar)
            GLib.idle_add(self._on_doctor_done, success, output)

        import threading
        threading.Thread(target=task, daemon=True).start()

    def _on_doctor_done(self, success: bool, output: str):
        self._append_output(output)
        if success:
            self._append_output("\n✓ Compatível")
        else:
            self._append_output("\n✗ Incompatível")
        self._set_buttons_sensitive(True)

    def _show_error(self, message: str):
        dialog = Adw.MessageDialog(
            transient_for=self,
            heading="Erro",
            body=message,
        )
        dialog.add_response("ok", "OK")
        dialog.set_default_response("ok")
        dialog.set_close_response("ok")
        dialog.present()


class Application(Adw.Application):
    def __init__(self):
        super().__init__(application_id="io.github.kristyancarvalho.brmgen.gtk")
        self.connect("activate", self.on_activate)

    def on_activate(self, app):
        win = MainWindow(application=app)
        win.present()


def main():
    app = Application()
    app.run(sys.argv)


if __name__ == "__main__":
    main()