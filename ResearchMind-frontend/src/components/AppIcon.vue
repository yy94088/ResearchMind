<template>
  <svg
    class="app-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="name === 'home'"><path d="M3 11.5 12 4l9 7.5" /><path d="M5.5 10v9h13v-9M9 19v-5h6v5" /></template>
    <template v-else-if="name === 'book'"><path d="M5 4.5h11a3 3 0 0 1 3 3V20H7a3 3 0 0 1-3-3V5.5a1 1 0 0 1 1-1Z" /><path d="M7 16h12M8 8h7M8 11h5" /></template>
    <template v-else-if="name === 'graph'"><circle cx="5" cy="6" r="2.3" /><circle cx="18.5" cy="5" r="2.3" /><circle cx="12" cy="18" r="2.3" /><path d="m7 7 4 9m5.5-9-3.4 9M7.3 6h8.9" /></template>
    <template v-else-if="name === 'chart'"><path d="M4 20V10m5 10V4m6 16v-7m5 7V7M2 20h20" /></template>
    <template v-else-if="name === 'sparkles'"><path d="m12 3 .8 2.3A5.8 5.8 0 0 0 16.5 9l2.3.8-2.3.8a5.8 5.8 0 0 0-3.7 3.7L12 17l-.8-2.7a5.8 5.8 0 0 0-3.7-3.7l-2.3-.8L7.5 9a5.8 5.8 0 0 0 3.7-3.7L12 3Z" /><path d="m19 15 .4 1.1a3 3 0 0 0 1.8 1.8l1.1.4-1.1.4a3 3 0 0 0-1.8 1.8L19 22l-.4-1.5a3 3 0 0 0-1.8-1.8l-1.1-.4 1.1-.4a3 3 0 0 0 1.8-1.8L19 15Z" /></template>
    <template v-else-if="name === 'users'"><path d="M16 20v-1.5a4.5 4.5 0 0 0-4.5-4.5h-4A4.5 4.5 0 0 0 3 18.5V20" /><circle cx="9.5" cy="7.5" r="3.5" /><path d="M16 4.3a3.5 3.5 0 0 1 0 6.4m5 9.3v-1.5a4.5 4.5 0 0 0-3.2-4.3" /></template>
    <template v-else-if="name === 'settings'"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.8 1.8 0 0 0 .4 2l.1.1-2.8 2.8-.1-.1a1.8 1.8 0 0 0-2-.4 1.8 1.8 0 0 0-1.1 1.6v.2h-4V21A1.8 1.8 0 0 0 8.8 19.4a1.8 1.8 0 0 0-2 .4l-.1.1-2.8-2.8L4 17a1.8 1.8 0 0 0 .4-2A1.8 1.8 0 0 0 2.8 14h-.2v-4h.2A1.8 1.8 0 0 0 4.4 9a1.8 1.8 0 0 0-.4-2l-.1-.1 2.8-2.8.1.1a1.8 1.8 0 0 0 2 .4A1.8 1.8 0 0 0 9.9 3v-.2h4V3A1.8 1.8 0 0 0 15 4.6a1.8 1.8 0 0 0 2-.4l.1-.1 2.8 2.8-.1.1a1.8 1.8 0 0 0-.4 2A1.8 1.8 0 0 0 21 10h.2v4H21a1.8 1.8 0 0 0-1.6 1Z" /></template>
    <template v-else-if="name === 'search'"><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></template>
    <template v-else-if="name === 'plus'"><path d="M12 5v14M5 12h14" /></template>
    <template v-else-if="name === 'upload'"><path d="M12 16V4m0 0L7 9m5-5 5 5" /><path d="M5 14v5h14v-5" /></template>
    <template v-else-if="name === 'bell'"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></template>
    <template v-else-if="name === 'star'"><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-2.9-5.6 2.9 1.1-6.2L3 9.6l6.2-.9L12 3Z" /></template>
    <template v-else-if="name === 'clock'"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></template>
    <template v-else-if="name === 'file'"><path d="M6 3h8l4 4v14H6z" /><path d="M14 3v5h4M9 13h6M9 17h6" /></template>
    <template v-else-if="name === 'chevron'"><path d="m9 18 6-6-6-6" /></template>
    <template v-else-if="name === 'more'"><circle cx="5" cy="12" r="1" fill="currentColor" stroke="none" /><circle cx="12" cy="12" r="1" fill="currentColor" stroke="none" /><circle cx="19" cy="12" r="1" fill="currentColor" stroke="none" /></template>
    <template v-else-if="name === 'trash'"><path d="M4 7h16M9 7V4h6v3m3 0-1 14H7L6 7m4 4v6m4-6v6" /></template>
    <template v-else-if="name === 'edit'"><path d="m4 20 4.2-1 10.6-10.6-3.2-3.2L5 15.8 4 20ZM14.5 6.3l3.2 3.2" /></template>
    <template v-else-if="name === 'check'"><path d="m5 12 4 4L19 6" /></template>
    <template v-else-if="name === 'logout'"><path d="M10 4H4v16h6m5-4 4-4-4-4m4 4H9" /></template>
    <template v-else-if="name === 'filter'"><path d="M4 5h16l-6 7v6l-4 2v-8L4 5Z" /></template>
    <template v-else-if="name === 'refresh'"><path d="M20 7v5h-5M4 17v-5h5" /><path d="M6.1 8A7 7 0 0 1 18 6l2 6M18 16a7 7 0 0 1-11.9 2L4 12" /></template>
    <template v-else-if="name === 'download'"><path d="M12 4v12m0 0 5-5m-5 5-5-5M5 20h14" /></template>
    <template v-else><circle cx="12" cy="12" r="9" /><path d="M12 8v4m0 4h.01" /></template>
  </svg>
</template>

<script setup>
defineProps({
  name: { type: String, required: true }
})
</script>
