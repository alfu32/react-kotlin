module main

import editor
import os
import term.ui as tui

// --------------------- Events ---------------------

pub enum UiEventKind {
	click
	mouse_move
	mouse_down
	mouse_up
	key_down
	key_up
	focus
	blur
}

pub struct KeyState {
pub mut:
	code  u32
	num   u32
	char  rune
	ctrl  bool
	alt   bool
	shift bool
	meta  bool
}

pub fn (ks KeyState) str() string {
	mut mods := 0
	if ks.shift {
		mods |= 8
	}
	if ks.ctrl {
		mods |= 4
	}
	if ks.meta {
		mods |= 2
	}
	if ks.alt {
		mods |= 1
	}
	return 'KeyState{code:0x${ks.code:02X},num:0x${ks.num:02X},char:${ks.char},mods:b${mods:04b}}'
}

pub struct MouseButtons {
pub mut:
	left   bool
	middle bool
	right  bool
}

pub fn (mb MouseButtons) str() string {
	mut mods := 0
	if mb.left {
		mods |= 4
	}
	if mb.middle {
		mods |= 2
	}
	if mb.right {
		mods |= 1
	}
	return 'MouseButtons{b${mods:03b}}'
}

pub struct MouseState {
pub mut:
	x       int
	y       int
	buttons MouseButtons
	wheel   int
}

pub fn (ms MouseState) str() string {
	return 'MouseState{x:${ms.x},y:${ms.y},buttons:${ms.buttons},wheel:${ms.wheel}}'
}

pub struct UiEvent {
pub mut:
	kind       UiEventKind
	target     int
	path       []int
	key        KeyState
	mouse      MouseState
	renderer   Renderer
	target_tag string
	propagate  bool = true
}

pub fn (ev UiEvent) str() string {
	return '
	UiEvent{
	kind:${ev.kind}, target:${ev.target}, target_tag:${ev.target_tag}, path:${ev.path},
	key:${ev.key},
	mouse:${ev.mouse},
	renderer:[Circular], propagate:${ev.propagate} }
	'.trim_indent()
}

pub type UiEventHandler = fn (mut UiEvent)

pub struct EventSpec {
pub mut:
	is_key       bool
	is_mouse     bool
	key_char     ?rune  // e.g. 'c' for "Ctrl-C"
	mouse_action string // "click", "down", "up", "move", "wheel" or ""
	req_ctrl     bool
	req_alt      bool
	req_shift    bool
	req_meta     bool
}

fn handle_editor_event(mut state LayoutState, width int, height int, work_left int, main_top int, mut e UiEvent) {
	if isnil(state.buffer) {
		return
	}
	if state.prompt.active {
		return
	}
	sync_viewport_from_renderer(mut state, e.renderer)
	if e.kind == .mouse_down {
		if editor_hit_test(state.editor_rect, e.mouse.x, e.mouse.y) {
			state.editor_focused = true
			pos := editor_position_from_mouse(state, e.mouse.x, e.mouse.y)
			state.buffer.start_selection(pos)
			state.editor_follow_cursor = true
			state.editor_dragging = e.mouse.buttons.left
			ensure_cursor_visible(mut state, width, height)
		} else {
			state.editor_focused = false
		}
		return
	}
	if e.kind == .mouse_move {
		if e.mouse.wheel != 0 && editor_hit_test(state.editor_rect, e.mouse.x, e.mouse.y) {
			state.editor_follow_cursor = false
			state.editor_view_y -= -3 * e.mouse.wheel
			clamp_editor_view(mut state, width, height)
		}
		if state.editor_dragging && e.mouse.buttons.left {
			pos := editor_position_from_mouse(state, e.mouse.x, e.mouse.y)
			state.buffer.select_to(pos)
			ensure_cursor_visible(mut state, width, height)
		} else if state.editor_dragging && !e.mouse.buttons.left {
			state.editor_dragging = false
		}
		return
	}
	if e.kind == .mouse_up {
		if state.editor_dragging {
			state.editor_dragging = false
		}
		return
	}
	if e.kind == .key_down && state.editor_focused {
		handle_editor_key(mut state, width, height, mut e)
		state.editor_follow_cursor = true
	}
}

fn handle_editor_key(mut state LayoutState, width int, height int, mut e UiEvent) {
	if isnil(state.buffer) {
		return
	}
	if state.prompt.active {
		return
	}
	mut handled := false
	mut content_changed := false
	ctrl := e.key.ctrl
	shift := e.key.shift
	code := int(e.key.code)
	match code {
		int(tui.KeyCode.left) {
			state.buffer.move_left(shift, ctrl)
			handled = true
		}
		int(tui.KeyCode.right) {
			state.buffer.move_right(shift, ctrl)
			handled = true
		}
		int(tui.KeyCode.up) {
			state.buffer.move_up(shift)
			handled = true
		}
		int(tui.KeyCode.down) {
			state.buffer.move_down(shift)
			handled = true
		}
		int(tui.KeyCode.home) {
			state.buffer.move_start_of_line(shift)
			handled = true
		}
		int(tui.KeyCode.end) {
			state.buffer.move_end_of_line(shift)
			handled = true
		}
		int(tui.KeyCode.enter) {
			state.buffer.insert_newline()
			handled = true
			content_changed = true
		}
		int(tui.KeyCode.backspace) {
			state.buffer.delete_backspace()
			handled = true
			content_changed = true
		}
		int(tui.KeyCode.delete) {
			state.buffer.delete_forward()
			handled = true
			content_changed = true
		}
		else {}
	}
	if ctrl {
		match code {
			int(tui.KeyCode.c) {
				state.buffer.copy_selection()
				handled = true
			}
			int(tui.KeyCode.x) {
				if state.buffer.cut_selection() {
					content_changed = true
					handled = true
				}
			}
			int(tui.KeyCode.v) {
				state.buffer.paste_clipboard()
				handled = true
				content_changed = true
			}
			int(tui.KeyCode.a) {
				state.buffer.select_all()
				handled = true
			}
			int(tui.KeyCode.s) {
				save_current_file(mut state)
				handled = true
			}
			else {}
		}
	}
	if !handled && !ctrl && !e.key.alt {
		mut ch := e.key.char.str()
		if ch.len > 0 && ch[0] >= 32 {
			state.buffer.insert_text(ch)
			handled = true
			content_changed = true
		}
	}
	if handled {
		ensure_cursor_visible(mut state, width, height)
	}
	if content_changed {
		state.buffer_dirty = true
	}
}

fn save_current_file(mut state LayoutState) {
	if isnil(state.buffer) || state.selected_file.len == 0 {
		state.status_message = 'No file selected'
		return
	}
	content := state.buffer.text()
	os.write_file(state.selected_file, content) or {
		state.status_message = 'Save failed: ${err.msg()}'
		return
	}
	state.status_message = 'Saved ${os.file_name(state.selected_file)}'
	state.buffer_dirty = false
}

fn autosave_current_file(mut state LayoutState) {
	if state.buffer_dirty {
		save_current_file(mut state)
	}
}

fn editor_position_from_mouse(state LayoutState, mouse_x int, mouse_y int) editor.Position {
	mut line := mouse_y - state.editor_rect.y + state.editor_view_y
	if line < 0 {
		line = 0
	}
	if line >= state.buffer.lines.len {
		line = state.buffer.lines.len - 1
		if line < 0 {
			line = 0
		}
	}
	mut visual_column_value := mouse_x - state.editor_rect.x - editor_gutter_chars +
		state.editor_view_x
	if visual_column_value < 0 {
		visual_column_value = 0
	}
	line_text := state.buffer.lines[line]
	mut max_visual := editor.visual_length(line_text)
	if visual_column_value > max_visual {
		visual_column_value = max_visual
	}
	actual_column := editor.actual_column(line_text, visual_column_value)
	return editor.Position{line, actual_column}
}

fn ensure_cursor_visible(mut state LayoutState, width int, height int) {
	mut visible_lines := if height > 0 { height } else { 1 }
	mut max_line := state.buffer.lines.len - visible_lines
	if max_line < 0 {
		max_line = 0
	}
	if state.buffer.cursor.line < state.editor_view_y {
		state.editor_view_y = state.buffer.cursor.line
	}
	if state.buffer.cursor.line >= state.editor_view_y + visible_lines {
		state.editor_view_y = state.buffer.cursor.line - visible_lines + 1
	}
	mut content_width := width - editor_gutter_chars
	if content_width <= 0 {
		content_width = 1
	}
	line_text := if state.buffer.cursor.line >= 0
		&& state.buffer.cursor.line < state.buffer.lines.len {
		state.buffer.lines[state.buffer.cursor.line]
	} else {
		''
	}
	cursor_visual := editor.visual_column(line_text, state.buffer.cursor.column)
	if cursor_visual < state.editor_view_x {
		state.editor_view_x = cursor_visual
	}
	if cursor_visual >= state.editor_view_x + content_width {
		state.editor_view_x = cursor_visual - content_width + 1
	}
	clamp_editor_view(mut state, width, height)
}
