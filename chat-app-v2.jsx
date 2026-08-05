import React, { useState, useEffect, useRef, useCallback } from "react";
import { Send, Circle, ArrowLeft, Plus, Users, Image as ImageIcon, X, MoreVertical, Check } from "lucide-react";

const ACCENT = "#ff6b35";
const BG = "#0b0d0f";
const PANEL = "#14171a";
const BORDER = "#22262b";
const REACT_EMOJIS = ["👍", "❤️", "😂", "🔥"];

const NAME_KEY = "wire:my-name";
const USERS_KEY = "wire:users";
const CONVS_KEY = "wire:convs";
const PRESENCE_PREFIX = "wire:presence:";
const TYPING_PREFIX = "wire:typing:";
const conv_key = (id) => `wire:conv:${id}`;

function useNow(tickMs = 15000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), tickMs);
    return () => clearInterval(id);
  }, [tickMs]);
  return now;
}
function timeAgo(ts, now) {
  const s = Math.floor((now - ts) / 1000);
  if (s < 5) return "now";
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h`;
  return new Date(ts).toLocaleDateString();
}
function clockTime(ts) {
  return new Date(ts).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}
function colorForName(name) {
  const palette = ["#ff6b35", "#4ecdc4", "#ffd93d", "#a685e2", "#6bcB77", "#ff9fb2"];
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
  return palette[h % palette.length];
}
function dmId(a, b) {
  return "dm:" + [a, b].sort().join("::");
}
async function compressImage(file, maxDim = 800, quality = 0.7) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new window.Image();
      img.onload = () => {
        let { width, height } = img;
        if (width > height && width > maxDim) {
          height = Math.round((height * maxDim) / width);
          width = maxDim;
        } else if (height > maxDim) {
          width = Math.round((width * maxDim) / height);
          height = maxDim;
        }
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        canvas.getContext("2d").drawImage(img, 0, 0, width, height);
        resolve(canvas.toDataURL("image/jpeg", quality));
      };
      img.onerror = reject;
      img.src = reader.result;
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

export default function ChatApp() {
  const [name, setName] = useState(null);
  const [nameDraft, setNameDraft] = useState("");
  const [view, setView] = useState("list"); // 'list' | 'chat'
  const [convs, setConvs] = useState([{ id: "general", type: "room", name: "General", members: [] }]);
  const [activeId, setActiveId] = useState("general");
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [online, setOnline] = useState([]);
  const [typingUsers, setTypingUsers] = useState([]);
  const [allUsers, setAllUsers] = useState([]);
  const [showNewModal, setShowNewModal] = useState(false);
  const [modalMode, setModalMode] = useState("dm"); // 'dm' | 'group'
  const [picked, setPicked] = useState([]);
  const [groupName, setGroupName] = useState("");
  const [menuFor, setMenuFor] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [editText, setEditText] = useState("");
  const [reactFor, setReactFor] = useState(null);
  const bottomRef = useRef(null);
  const fileInputRef = useRef(null);
  const now = useNow();
  const activeConv = convs.find((c) => c.id === activeId) || convs[0];

  // ---- boot: load saved name ----
  useEffect(() => {
    (async () => {
      try {
        const r = await window.storage.get(NAME_KEY, false);
        if (r && r.value) setName(r.value);
      } catch (e) {}
    })();
  }, []);

  // ---- register user + load directory ----
  const registerUser = useCallback(async () => {
    if (!name) return;
    try {
      const r = await window.storage.get(USERS_KEY, true);
      const list = r && r.value ? JSON.parse(r.value) : [];
      if (!list.includes(name)) {
        list.push(name);
        await window.storage.set(USERS_KEY, JSON.stringify(list), true);
      }
      setAllUsers(list.filter((u) => u !== name));
    } catch (e) {
      try {
        await window.storage.set(USERS_KEY, JSON.stringify([name]), true);
      } catch (e2) {}
    }
  }, [name]);

  const fetchConvs = useCallback(async () => {
    try {
      const r = await window.storage.get(CONVS_KEY, true);
      const stored = r && r.value ? JSON.parse(r.value) : [];
      const hasGeneral = stored.some((c) => c.id === "general");
      const merged = hasGeneral ? stored : [{ id: "general", type: "room", name: "General", members: [] }, ...stored];
      const mine = merged.filter(
        (c) => c.type === "room" || (c.members && c.members.includes(name))
      );
      setConvs(mine);
    } catch (e) {}
  }, [name]);

  const fetchMessages = useCallback(async (id) => {
    try {
      const r = await window.storage.get(conv_key(id), true);
      const list = r && r.value ? JSON.parse(r.value) : [];
      setMessages(list);
      setError(null);
    } catch (e) {
      setMessages([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const beacon = useCallback(async () => {
    if (!name) return;
    try {
      await window.storage.set(PRESENCE_PREFIX + name, String(Date.now()), true);
      const listRes = await window.storage.list(PRESENCE_PREFIX, true);
      if (listRes && listRes.keys) {
        const entries = await Promise.all(
          listRes.keys.map(async (k) => {
            try {
              const v = await window.storage.get(k, true);
              return { name: k.replace(PRESENCE_PREFIX, ""), ts: parseInt(v.value, 10) };
            } catch {
              return null;
            }
          })
        );
        setOnline(entries.filter((e) => e && Date.now() - e.ts < 30000).map((e) => e.name));
      }
    } catch (e) {}
  }, [name]);

  const fetchTyping = useCallback(async (id) => {
    if (!id) return;
    try {
      const listRes = await window.storage.list(TYPING_PREFIX + id + ":", true);
      if (!listRes || !listRes.keys) return setTypingUsers([]);
      const entries = await Promise.all(
        listRes.keys.map(async (k) => {
          try {
            const v = await window.storage.get(k, true);
            return { who: k.split(":").pop(), ts: parseInt(v.value, 10) };
          } catch {
            return null;
          }
        })
      );
      setTypingUsers(
        entries.filter((e) => e && e.who !== name && Date.now() - e.ts < 4000).map((e) => e.who)
      );
    } catch (e) {}
  }, [name]);

  useEffect(() => {
    if (!name) return;
    registerUser();
    fetchConvs();
    beacon();
    const t1 = setInterval(fetchConvs, 4000);
    const t2 = setInterval(beacon, 8000);
    return () => {
      clearInterval(t1);
      clearInterval(t2);
    };
  }, [name, registerUser, fetchConvs, beacon]);

  useEffect(() => {
    if (!name || !activeId) return;
    setLoading(true);
    fetchMessages(activeId);
    fetchTyping(activeId);
    const t1 = setInterval(() => fetchMessages(activeId), 1500);
    const t2 = setInterval(() => fetchTyping(activeId), 2000);
    return () => {
      clearInterval(t1);
      clearInterval(t2);
    };
  }, [activeId, name, fetchMessages, fetchTyping]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length, view]);

  async function claimName() {
    const trimmed = nameDraft.trim().slice(0, 20);
    if (!trimmed) return;
    try {
      await window.storage.set(NAME_KEY, trimmed, false);
    } catch (e) {}
    setName(trimmed);
  }

  async function saveConvsList(next) {
    setConvs(next.filter((c) => c.type === "room" || (c.members && c.members.includes(name))));
    try {
      const r = await window.storage.get(CONVS_KEY, true).catch(() => null);
      const stored = r && r.value ? JSON.parse(r.value) : [];
      const others = stored.filter((c) => !next.some((n) => n.id === c.id));
      await window.storage.set(CONVS_KEY, JSON.stringify([...others, ...next]), true);
    } catch (e) {}
  }

  async function openDM(other) {
    const id = dmId(name, other);
    if (!convs.some((c) => c.id === id)) {
      const newConv = { id, type: "dm", name: other, members: [name, other] };
      await saveConvsList([...convs, newConv]);
    }
    setActiveId(id);
    setView("chat");
    setShowNewModal(false);
    setPicked([]);
  }

  async function createGroup() {
    if (picked.length < 2 || !groupName.trim()) return;
    const id = `group:${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
    const newConv = {
      id,
      type: "group",
      name: groupName.trim().slice(0, 40),
      members: [name, ...picked],
    };
    await saveConvsList([...convs, newConv]);
    setActiveId(id);
    setView("chat");
    setShowNewModal(false);
    setPicked([]);
    setGroupName("");
  }

  async function persistMessages(id, list) {
    await window.storage.set(conv_key(id), JSON.stringify(list.slice(-200)), true);
  }

  async function sendMessage(imageDataUrl) {
    const text = draft.trim();
    if (!text && !imageDataUrl) return;
    if (!activeId) return;
    setDraft("");
    const msg = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      author: name,
      text,
      image: imageDataUrl || null,
      ts: Date.now(),
      reactions: {},
      edited: false,
      deleted: false,
    };
    setMessages((prev) => [...prev, msg]);
    try {
      const r = await window.storage.get(conv_key(activeId), true).catch(() => null);
      const current = r && r.value ? JSON.parse(r.value) : [];
      await persistMessages(activeId, [...current, msg]);
      fetchMessages(activeId);
    } catch (e) {
      setError("Message didn't send — check your connection and try again.");
    }
    try {
      await window.storage.delete(TYPING_PREFIX + activeId + ":" + name, true);
    } catch (e) {}
  }

  async function handlePickImage(e) {
    const file = e.target.files && e.target.files[0];
    e.target.value = "";
    if (!file) return;
    try {
      const dataUrl = await compressImage(file);
      await sendMessage(dataUrl);
    } catch (e) {
      setError("Couldn't process that image.");
    }
  }

  let typingTimer = useRef(null);
  function handleDraftChange(v) {
    setDraft(v);
    if (!activeId) return;
    window.storage.set(TYPING_PREFIX + activeId + ":" + name, String(Date.now()), true).catch(() => {});
    clearTimeout(typingTimer.current);
    typingTimer.current = setTimeout(() => {
      window.storage.delete(TYPING_PREFIX + activeId + ":" + name, true).catch(() => {});
    }, 3000);
  }

  function handleKeyDown(e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  }

  async function updateMessage(id, patch) {
    setMessages((prev) => prev.map((m) => (m.id === id ? { ...m, ...patch } : m)));
    try {
      const r = await window.storage.get(conv_key(activeId), true);
      const current = r && r.value ? JSON.parse(r.value) : [];
      const next = current.map((m) => (m.id === id ? { ...m, ...patch } : m));
      await persistMessages(activeId, next);
    } catch (e) {}
  }

  function saveEdit(id) {
    const clean = editText.trim();
    if (!clean) return;
    updateMessage(id, { text: clean, edited: true });
    setEditingId(null);
    setEditText("");
  }

  function deleteMsg(id) {
    updateMessage(id, { deleted: true, text: "", image: null, reactions: {} });
    setMenuFor(null);
  }

  async function toggleReaction(msg, emoji) {
    const reactions = { ...(msg.reactions || {}) };
    const arr = reactions[emoji] ? [...reactions[emoji]] : [];
    const idx = arr.indexOf(name);
    if (idx === -1) arr.push(name);
    else arr.splice(idx, 1);
    if (arr.length) reactions[emoji] = arr;
    else delete reactions[emoji];
    await updateMessage(msg.id, { reactions });
    setReactFor(null);
  }

  function convLabel(c) {
    if (!c) return "";
    if (c.type === "room") return c.name;
    if (c.type === "dm") return c.name;
    return c.name || c.members.filter((m) => m !== name).join(", ");
  }

  // ---- Name entry screen ----
  if (!name) {
    return (
      <div style={{ minHeight: "100vh", background: BG, display: "flex", alignItems: "center", justifyContent: "center", padding: 24, fontFamily: "'Inter', system-ui, sans-serif" }}>
        <div style={{ width: "100%", maxWidth: 360 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 28, justifyContent: "center" }}>
            <div style={{ width: 10, height: 10, borderRadius: "50%", background: ACCENT, boxShadow: `0 0 12px ${ACCENT}` }} />
            <span style={{ color: "#8a8f94", fontSize: 13, letterSpacing: "0.2em", textTransform: "uppercase", fontFamily: "'JetBrains Mono', monospace" }}>Wire</span>
          </div>
          <h1 style={{ color: "#fff", fontSize: 28, fontWeight: 700, marginBottom: 8, letterSpacing: "-0.02em" }}>What should we call you?</h1>
          <p style={{ color: "#8a8f94", fontSize: 14, marginBottom: 24, lineHeight: 1.5 }}>Your name is shown next to your messages and lets others start DMs with you.</p>
          <input autoFocus value={nameDraft} onChange={(e) => setNameDraft(e.target.value)} onKeyDown={(e) => e.key === "Enter" && claimName()} placeholder="Your name" maxLength={20}
            style={{ width: "100%", padding: "14px 16px", borderRadius: 12, border: `1px solid ${BORDER}`, background: PANEL, color: "#fff", fontSize: 16, outline: "none", boxSizing: "border-box", marginBottom: 16 }} />
          <button onClick={claimName} disabled={!nameDraft.trim()}
            style={{ width: "100%", padding: "14px 16px", borderRadius: 12, border: "none", background: nameDraft.trim() ? ACCENT : "#3a3d40", color: "#fff", fontSize: 16, fontWeight: 600, cursor: nameDraft.trim() ? "pointer" : "not-allowed" }}>
            Enter room
          </button>
        </div>
      </div>
    );
  }

  // ---- New DM/Group modal ----
  const modal = showNewModal && (
    <div style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.6)", display: "flex", alignItems: "flex-end", zIndex: 20 }} onClick={() => setShowNewModal(false)}>
      <div style={{ background: PANEL, width: "100%", borderRadius: "20px 20px 0 0", padding: 20, maxHeight: "75vh", overflowY: "auto" }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={() => setModalMode("dm")} style={{ padding: "7px 14px", borderRadius: 20, border: `1px solid ${modalMode === "dm" ? ACCENT : BORDER}`, background: modalMode === "dm" ? ACCENT : "transparent", color: "#fff", fontSize: 13, fontWeight: 600 }}>Direct message</button>
            <button onClick={() => setModalMode("group")} style={{ padding: "7px 14px", borderRadius: 20, border: `1px solid ${modalMode === "group" ? ACCENT : BORDER}`, background: modalMode === "group" ? ACCENT : "transparent", color: "#fff", fontSize: 13, fontWeight: 600 }}>Group</button>
          </div>
          <button onClick={() => setShowNewModal(false)} style={{ background: "none", border: "none", color: "#8a8f94" }}><X size={20} /></button>
        </div>

        {modalMode === "group" && (
          <input value={groupName} onChange={(e) => setGroupName(e.target.value)} placeholder="Group name" maxLength={40}
            style={{ width: "100%", padding: "12px 14px", borderRadius: 10, border: `1px solid ${BORDER}`, background: BG, color: "#fff", fontSize: 15, outline: "none", boxSizing: "border-box", marginBottom: 14 }} />
        )}

        {allUsers.length === 0 && <div style={{ color: "#8a8f94", fontSize: 13, textAlign: "center", padding: "20px 0" }}>No one else has joined yet. Share the app link to get started.</div>}

        {allUsers.map((u) => {
          const isPicked = picked.includes(u);
          return (
            <div key={u} onClick={() => {
              if (modalMode === "dm") { openDM(u); return; }
              setPicked((p) => (isPicked ? p.filter((x) => x !== u) : [...p, u]));
            }}
              style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 4px", borderBottom: `1px solid ${BORDER}`, cursor: "pointer" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ width: 32, height: 32, borderRadius: "50%", background: colorForName(u), display: "flex", alignItems: "center", justifyContent: "center", color: "#fff", fontSize: 13, fontWeight: 700 }}>{u[0]?.toUpperCase()}</div>
                <span style={{ color: "#fff", fontSize: 15 }}>{u}</span>
              </div>
              {modalMode === "group" && isPicked && <Check size={18} color={ACCENT} />}
            </div>
          );
        })}

        {modalMode === "group" && (
          <button onClick={createGroup} disabled={picked.length < 2 || !groupName.trim()}
            style={{ width: "100%", marginTop: 16, padding: "13px", borderRadius: 12, border: "none", background: picked.length >= 2 && groupName.trim() ? ACCENT : "#3a3d40", color: "#fff", fontWeight: 600, fontSize: 15 }}>
            Create group ({picked.length} picked)
          </button>
        )}
      </div>
    </div>
  );

  // ---- Conversation list screen ----
  if (view === "list") {
    return (
      <div style={{ height: "100vh", background: BG, display: "flex", flexDirection: "column", fontFamily: "'Inter', system-ui, sans-serif" }}>
        <div style={{ padding: "16px 18px", borderBottom: `1px solid ${BORDER}`, display: "flex", alignItems: "center", justifyContent: "space-between", background: PANEL, flexShrink: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <div style={{ width: 9, height: 9, borderRadius: "50%", background: ACCENT, boxShadow: `0 0 10px ${ACCENT}` }} />
            <div>
              <div style={{ color: "#fff", fontWeight: 700, fontSize: 15 }}>Wire</div>
              <div style={{ color: "#8a8f94", fontSize: 11, fontFamily: "'JetBrains Mono', monospace" }}>{online.length} online</div>
            </div>
          </div>
          <button onClick={() => setShowNewModal(true)} style={{ width: 36, height: 36, borderRadius: "50%", border: "none", background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Plus size={18} color="#fff" />
          </button>
        </div>
        <div style={{ flex: 1, overflowY: "auto" }}>
          {convs.map((c) => (
            <div key={c.id} onClick={() => { setActiveId(c.id); setView("chat"); }}
              style={{ display: "flex", alignItems: "center", gap: 12, padding: "14px 18px", borderBottom: `1px solid ${BORDER}`, cursor: "pointer" }}>
              <div style={{ width: 42, height: 42, borderRadius: c.type === "group" ? 12 : "50%", background: c.type === "room" ? "#2a2d30" : colorForName(convLabel(c)), display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                {c.type === "group" ? <Users size={18} color="#fff" /> : <span style={{ color: "#fff", fontWeight: 700, fontSize: 15 }}>{convLabel(c)[0]?.toUpperCase()}</span>}
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ color: "#fff", fontWeight: 600, fontSize: 15 }}>{convLabel(c)}</div>
                <div style={{ color: "#8a8f94", fontSize: 12 }}>{c.type === "room" ? "Public room" : c.type === "group" ? `${c.members.length} members` : "Direct message"}</div>
              </div>
            </div>
          ))}
        </div>
        {modal}
      </div>
    );
  }

  // ---- Chat screen ----
  return (
    <div style={{ height: "100vh", background: BG, display: "flex", flexDirection: "column", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ padding: "14px 14px", borderBottom: `1px solid ${BORDER}`, display: "flex", alignItems: "center", gap: 10, background: PANEL, flexShrink: 0 }}>
        <button onClick={() => setView("list")} style={{ background: "none", border: "none", color: "#8a8f94", display: "flex" }}><ArrowLeft size={20} /></button>
        <div style={{ width: 34, height: 34, borderRadius: activeConv?.type === "group" ? 10 : "50%", background: activeConv?.type === "room" ? "#2a2d30" : colorForName(convLabel(activeConv)), display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
          {activeConv?.type === "group" ? <Users size={15} color="#fff" /> : <span style={{ color: "#fff", fontWeight: 700, fontSize: 13 }}>{convLabel(activeConv)[0]?.toUpperCase()}</span>}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ color: "#fff", fontWeight: 700, fontSize: 15 }}>{convLabel(activeConv)}</div>
          <div style={{ color: "#8a8f94", fontSize: 11, fontFamily: "'JetBrains Mono', monospace" }}>
            {typingUsers.length > 0 ? `${typingUsers.join(", ")} typing…` : activeConv?.type === "room" ? `${online.length} online` : " "}
          </div>
        </div>
      </div>

      <div style={{ flex: 1, overflowY: "auto", padding: "16px 14px" }}>
        {loading && <div style={{ color: "#8a8f94", textAlign: "center", marginTop: 40, fontSize: 13 }}>Loading messages…</div>}
        {!loading && messages.length === 0 && (
          <div style={{ color: "#8a8f94", textAlign: "center", marginTop: 60, fontSize: 14, lineHeight: 1.6 }}>
            <Circle size={20} color={ACCENT} style={{ marginBottom: 10 }} />
            <div>No messages yet.</div>
            <div>Say something to get things started.</div>
          </div>
        )}
        {messages.map((m, i) => {
          const isMe = m.author === name;
          const prev = messages[i - 1];
          const showAuthor = !prev || prev.author !== m.author;
          const reactionEntries = Object.entries(m.reactions || {});
          return (
            <div key={m.id || i} style={{ display: "flex", flexDirection: "column", alignItems: isMe ? "flex-end" : "flex-start", marginTop: showAuthor ? 14 : 3, position: "relative" }}>
              {showAuthor && (
                <div style={{ fontSize: 11, fontWeight: 600, marginBottom: 4, color: isMe ? "#8a8f94" : colorForName(m.author), padding: "0 4px" }}>
                  {isMe ? "You" : m.author}
                </div>
              )}

              {m.deleted ? (
                <div style={{ maxWidth: "78%", padding: "9px 13px", borderRadius: 14, background: "transparent", border: `1px dashed ${BORDER}`, color: "#5c6064", fontSize: 13, fontStyle: "italic" }}>
                  Message deleted
                </div>
              ) : editingId === m.id ? (
                <div style={{ maxWidth: "82%", display: "flex", gap: 6 }}>
                  <input autoFocus value={editText} onChange={(e) => setEditText(e.target.value)} onKeyDown={(e) => e.key === "Enter" && saveEdit(m.id)}
                    style={{ padding: "8px 12px", borderRadius: 10, border: `1px solid ${ACCENT}`, background: BG, color: "#fff", fontSize: 14 }} />
                  <button onClick={() => saveEdit(m.id)} style={{ background: ACCENT, border: "none", borderRadius: 8, color: "#fff", padding: "0 10px", fontSize: 12, fontWeight: 600 }}>Save</button>
                </div>
              ) : (
                <div onClick={() => setReactFor(reactFor === m.id ? null : m.id)}
                  style={{ maxWidth: "78%", padding: m.image ? 5 : "9px 13px", borderRadius: isMe ? "14px 14px 4px 14px" : "14px 14px 14px 4px", background: isMe ? ACCENT : PANEL, border: isMe ? "none" : `1px solid ${BORDER}`, color: "#fff", fontSize: 14.5, lineHeight: 1.4, wordBreak: "break-word", cursor: "pointer", position: "relative" }}>
                  {m.image && <img src={m.image} alt="" style={{ width: "100%", borderRadius: 10, display: "block", marginBottom: m.text ? 6 : 0 }} />}
                  {m.text}
                  {m.edited && <span style={{ fontSize: 10, opacity: 0.6, marginLeft: 6 }}>(edited)</span>}

                  {isMe && (
                    <button onClick={(e) => { e.stopPropagation(); setMenuFor(menuFor === m.id ? null : m.id); }}
                      style={{ position: "absolute", top: -6, left: -30, background: "none", border: "none", color: "#8a8f94" }}>
                      <MoreVertical size={16} />
                    </button>
                  )}
                  {menuFor === m.id && (
                    <div style={{ position: "absolute", top: -8, left: isMe ? "auto" : 0, right: isMe ? "100%" : "auto", marginRight: isMe ? 6 : 0, background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 10, overflow: "hidden", zIndex: 5 }}>
                      <div onClick={(e) => { e.stopPropagation(); setEditingId(m.id); setEditText(m.text); setMenuFor(null); }} style={{ padding: "8px 14px", color: "#fff", fontSize: 13, cursor: "pointer", whiteSpace: "nowrap" }}>Edit</div>
                      <div onClick={(e) => { e.stopPropagation(); deleteMsg(m.id); }} style={{ padding: "8px 14px", color: "#ff6b6b", fontSize: 13, cursor: "pointer", whiteSpace: "nowrap" }}>Delete</div>
                    </div>
                  )}
                </div>
              )}

              {reactFor === m.id && !m.deleted && (
                <div style={{ display: "flex", gap: 4, marginTop: 5, background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 20, padding: "4px 8px" }}>
                  {REACT_EMOJIS.map((em) => (
                    <span key={em} onClick={() => toggleReaction(m, em)} style={{ cursor: "pointer", fontSize: 16 }}>{em}</span>
                  ))}
                </div>
              )}

              {reactionEntries.length > 0 && (
                <div style={{ display: "flex", gap: 4, marginTop: 4, flexWrap: "wrap" }}>
                  {reactionEntries.map(([emoji, people]) => (
                    <div key={emoji} onClick={() => toggleReaction(m, emoji)} style={{ display: "flex", alignItems: "center", gap: 3, background: PANEL, border: `1px solid ${people.includes(name) ? ACCENT : BORDER}`, borderRadius: 12, padding: "2px 7px", fontSize: 11, color: "#fff", cursor: "pointer" }}>
                      <span>{emoji}</span><span>{people.length}</span>
                    </div>
                  ))}
                </div>
              )}

              {!m.deleted && (
                <div style={{ fontSize: 10, color: "#5c6064", marginTop: 3, padding: "0 4px", fontFamily: "'JetBrains Mono', monospace" }}>
                  {clockTime(m.ts)} · {timeAgo(m.ts, now)} ago
                </div>
              )}
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      {error && <div style={{ color: "#ff6b6b", fontSize: 12, padding: "0 16px 8px", textAlign: "center" }}>{error}</div>}

      <div style={{ padding: "12px 14px", borderTop: `1px solid ${BORDER}`, background: PANEL, display: "flex", gap: 8, flexShrink: 0, alignItems: "flex-end" }}>
        <input ref={fileInputRef} type="file" accept="image/*" onChange={handlePickImage} style={{ display: "none" }} />
        <button onClick={() => fileInputRef.current?.click()} style={{ width: 40, height: 40, borderRadius: "50%", border: `1px solid ${BORDER}`, background: BG, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
          <ImageIcon size={17} color="#8a8f94" />
        </button>
        <textarea value={draft} onChange={(e) => handleDraftChange(e.target.value)} onKeyDown={handleKeyDown} placeholder="Message..." rows={1}
          style={{ flex: 1, resize: "none", padding: "11px 14px", borderRadius: 20, border: `1px solid ${BORDER}`, background: BG, color: "#fff", fontSize: 15, outline: "none", fontFamily: "inherit", maxHeight: 100 }} />
        <button onClick={() => sendMessage()} disabled={!draft.trim()}
          style={{ width: 44, height: 44, borderRadius: "50%", border: "none", background: draft.trim() ? ACCENT : "#3a3d40", display: "flex", alignItems: "center", justifyContent: "center", cursor: draft.trim() ? "pointer" : "not-allowed", flexShrink: 0 }}>
          <Send size={18} color="#fff" />
        </button>
      </div>
      {modal}
    </div>
  );
}
