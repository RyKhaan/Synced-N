import {
  escapeHtml,
  isWorshipLeader,
  isSubstituteLeader,
  hasLeaderPrivileges,
  formatFriendlyDate,
  formatFriendlyTime,
  formatRelativeTime
} from "./utils.js";
import { DEFAULT_MINISTRY_ROLES } from "./config.js";

export function getAvatarHtml(user, sizeClass = "w-9 h-9", textSize = "text-xs") {
  const name = user?.fullName || user?.name || user?.username || "Member";
  const photo = user?.photoUri || user?.profilePictureUrl || "";
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join("") || "M";

  if (photo && photo.trim()) {
    return `<img src="${escapeHtml(photo)}" alt="${escapeHtml(name)}" class="${sizeClass} rounded-full object-cover border border-slate-200 shrink-0 bg-slate-100" />`;
  }
  return `
    <div class="${sizeClass} rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center font-semibold ${textSize} text-slate-700 shrink-0">
      ${escapeHtml(initials)}
    </div>
  `;
}

export function getRoleBadgeHtml(user) {
  if (!user) return "";
  const ministryRole = user.ministryRole || "Member";
  const authRole = user.role || "Member";

  if (isWorshipLeader(ministryRole, authRole)) {
    return `<span class="badge badge-indigo"><i class="fa-solid fa-shield-halved text-[10px]"></i> Worship Leader</span>`;
  }
  if (isSubstituteLeader(ministryRole)) {
    return `<span class="badge badge-amber"><i class="fa-solid fa-user-check text-[10px]"></i> Substitute Leader</span>`;
  }
  return `<span class="badge badge-neutral">${escapeHtml(ministryRole)}</span>`;
}

export function renderHomeView(state) {
  const { currentUser, currentTeam, teamMembers, services, announcements, songs, substitutions, rejoinRequests } = state;
  const isLeader = hasLeaderPrivileges(currentUser);
  const myAssignedServices = services.filter((s) =>
    (s.assignments || []).some((m) => m.uid === currentUser.uid)
  );
  const pendingSubs = substitutions.filter((s) => s.status === "Pending" || s.status === "Substitute Accepted");
  const pendingRejoins = rejoinRequests.filter((r) => r.status === "Pending");
  const teamDisplayName = currentTeam?.teamName || currentUser?.teamName || "Worship Ministry Team";
  const teamCode = currentTeam?.teamCode || currentUser?.teamCode || "------";

  return `
    <div class="space-y-6 animate-fade-in">
      <!-- Workspace Header Bar -->
      <div class="surface-card p-5 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
        <div class="space-y-1">
          <div class="flex flex-wrap items-center gap-2">
            <span class="text-xs font-medium text-slate-500">Ministry Workspace</span>
            <span class="text-slate-300">/</span>
            ${getRoleBadgeHtml(currentUser)}
          </div>
          <div class="flex items-center gap-2.5">
            <h1 class="text-xl sm:text-2xl font-semibold text-slate-900 tracking-tight">
              ${escapeHtml(teamDisplayName)}
            </h1>
            ${isLeader ? `
              <button onclick="window.appActions.openEditTeamNameModal()" class="btn btn-ghost btn-sm text-slate-500" title="Rename Team">
                <i class="fa-solid fa-pen text-xs"></i>
              </button>
            ` : ""}
          </div>
          <p class="text-xs text-slate-500">
            Signed in as <span class="font-medium text-slate-700">${escapeHtml(currentUser.fullName || currentUser.name || "Member")}</span>
            &middot; Schedule, lineup assignments, and chord sheets are synced in real time with the mobile app.
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-2.5">
          <!-- Team Invite Code Box -->
          <div class="flex items-center gap-2.5 bg-slate-50 border border-slate-200 rounded-md px-3 py-1.5">
            <div>
              <div class="text-[10px] uppercase tracking-wider text-slate-400 font-semibold leading-none">Invite Code</div>
              <div class="font-mono text-sm font-semibold text-slate-800 tracking-widest mt-0.5">${escapeHtml(teamCode)}</div>
            </div>
            <button onclick="window.appActions.copyTeamCode()" class="btn btn-secondary btn-sm" title="Copy Team Code">
              <i class="fa-regular fa-copy text-xs"></i> Copy
            </button>
          </div>

          <button onclick="window.appActions.openAvailabilityModal()" class="btn btn-secondary">
            <i class="fa-regular fa-calendar-xmark text-slate-500"></i> My Unavailability
          </button>

          ${isLeader ? `
            <button onclick="window.appActions.openServiceModal()" class="btn btn-primary">
              <i class="fa-solid fa-plus text-xs"></i> Schedule Service
            </button>
          ` : ""}
        </div>
      </div>

      <!-- Compact Summary Strip -->
      <div class="surface-card grid grid-cols-2 lg:grid-cols-4 divide-y sm:divide-y-0 sm:divide-x divide-slate-200">
        <button onclick="window.appActions.switchTab('schedule')" class="p-4 text-left hover:bg-slate-50/80 transition flex items-center justify-between">
          <div>
            <div class="text-xs font-medium text-slate-500">Upcoming Services</div>
            <div class="text-xl font-semibold text-slate-900 mt-0.5">${services.length}</div>
          </div>
          <div class="w-8 h-8 rounded-md bg-slate-100 text-slate-600 flex items-center justify-center text-xs">
            <i class="fa-regular fa-calendar-check"></i>
          </div>
        </button>

        <button onclick="window.appActions.switchTab('schedule')" class="p-4 text-left hover:bg-slate-50/80 transition flex items-center justify-between">
          <div>
            <div class="text-xs font-medium text-slate-500">My Lineup Slots</div>
            <div class="text-xl font-semibold text-indigo-700 mt-0.5">${myAssignedServices.length}</div>
          </div>
          <div class="w-8 h-8 rounded-md bg-indigo-50 text-indigo-700 flex items-center justify-center text-xs">
            <i class="fa-solid fa-user-check"></i>
          </div>
        </button>

        <button onclick="window.appActions.switchTab('songs')" class="p-4 text-left hover:bg-slate-50/80 transition flex items-center justify-between">
          <div>
            <div class="text-xs font-medium text-slate-500">Song Library</div>
            <div class="text-xl font-semibold text-slate-900 mt-0.5">${songs.length}</div>
          </div>
          <div class="w-8 h-8 rounded-md bg-slate-100 text-slate-600 flex items-center justify-center text-xs">
            <i class="fa-solid fa-music"></i>
          </div>
        </button>

        <button onclick="window.appActions.switchTab('roster')" class="p-4 text-left hover:bg-slate-50/80 transition flex items-center justify-between">
          <div>
            <div class="text-xs font-medium text-slate-500">Ministry Roster</div>
            <div class="text-xl font-semibold text-slate-900 mt-0.5">${teamMembers.length}</div>
          </div>
          <div class="w-8 h-8 rounded-md bg-slate-100 text-slate-600 flex items-center justify-center text-xs">
            <i class="fa-solid fa-users"></i>
          </div>
        </button>
      </div>

      <!-- Main Two-Column Content Grid -->
      <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <!-- Left Column (7 cols): Upcoming Worship Services & Lineups -->
        <div class="lg:col-span-7 space-y-4">
          <div class="flex items-center justify-between">
            <div>
              <h2 class="text-sm font-semibold text-slate-900">Upcoming Worship Services & Lineups</h2>
              <p class="text-xs text-slate-500">Scheduled services, rehearsals, assigned positions, and song setlists</p>
            </div>
            <button onclick="window.appActions.switchTab('schedule')" class="btn btn-ghost btn-sm text-indigo-700">
              View All (${services.length}) <i class="fa-solid fa-arrow-right text-[10px]"></i>
            </button>
          </div>

          ${services.length === 0 ? `
            <div class="surface-card p-8 text-center">
              <div class="w-10 h-10 rounded-md bg-slate-100 border border-slate-200 flex items-center justify-center mx-auto mb-3 text-slate-400">
                <i class="fa-regular fa-calendar"></i>
              </div>
              <h3 class="text-sm font-semibold text-slate-800">No services scheduled yet</h3>
              <p class="text-xs text-slate-500 mt-1 max-w-sm mx-auto">Create a worship service schedule to assign team members, set rehearsal times, and organize the song lineup.</p>
              ${isLeader ? `
                <button onclick="window.appActions.openServiceModal()" class="mt-4 btn btn-primary btn-sm">
                  <i class="fa-solid fa-plus text-[10px]"></i> Create First Service
                </button>
              ` : ""}
            </div>
          ` : `
            <div class="space-y-3">
              ${services.slice(0, 3).map((srv) => renderServiceCardHtml(srv, state, isLeader)).join("")}
            </div>
          `}
        </div>

        <!-- Right Column (5 cols): Action Items & Announcements -->
        <div class="lg:col-span-5 space-y-6">
          <!-- Pending Requests Alert Card (Leaders Only) -->
          ${isLeader && (pendingSubs.length > 0 || pendingRejoins.length > 0) ? `
            <div class="surface-card overflow-hidden border-amber-200">
              <div class="px-4 py-3 bg-amber-50/80 border-b border-amber-200 flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <i class="fa-solid fa-circle-exclamation text-amber-600 text-xs"></i>
                  <h3 class="text-xs font-semibold text-amber-900 uppercase tracking-wider">Attention Required</h3>
                </div>
                <span class="badge badge-amber">${pendingSubs.length + pendingRejoins.length} Pending</span>
              </div>
              <div class="divide-y divide-slate-100">
                ${pendingSubs.map((sub) => `
                  <div class="p-3.5 flex flex-col gap-2">
                    <div class="flex items-start justify-between gap-2">
                      <div>
                        <span class="text-xs font-semibold text-slate-900">${escapeHtml(sub.requesterName || "Member")}</span>
                        <span class="text-xs text-slate-500"> requested a substitute (${escapeHtml(sub.role || "Member")}) for </span>
                        <span class="text-xs font-medium text-slate-800">${escapeHtml(sub.serviceTitle || "Service")}</span>
                      </div>
                      <span class="badge badge-amber">Sub Request</span>
                    </div>
                    <p class="text-xs text-slate-600 bg-slate-50 px-2.5 py-1.5 rounded border border-slate-200">
                      "${escapeHtml(sub.reason || "Schedule conflict")}"
                      ${sub.substituteName ? ` &middot; Proposed Sub: <strong class="text-slate-800">${escapeHtml(sub.substituteName)}</strong>` : ""}
                    </p>
                    <div class="flex items-center gap-2 pt-0.5">
                      <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Approved')" class="btn btn-primary btn-sm">
                        <i class="fa-solid fa-check text-[10px]"></i> Approve
                      </button>
                      <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Declined')" class="btn btn-danger btn-sm">
                        Decline
                      </button>
                    </div>
                  </div>
                `).join("")}

                ${pendingRejoins.map((req) => `
                  <div class="p-3.5 flex items-center justify-between gap-3">
                    <div>
                      <div class="text-xs font-semibold text-slate-900">${escapeHtml(req.userName || "Former Member")}</div>
                      <div class="text-[11px] text-slate-500">Requested to rejoin the team</div>
                    </div>
                    <div class="flex items-center gap-1.5">
                      <button onclick="window.appActions.handleRejoinRequest('${escapeHtml(req.id)}', true)" class="btn btn-primary btn-sm">Approve</button>
                      <button onclick="window.appActions.handleRejoinRequest('${escapeHtml(req.id)}', false)" class="btn btn-danger btn-sm">Decline</button>
                    </div>
                  </div>
                `).join("")}
              </div>
            </div>
          ` : ""}

          <!-- Announcements Section -->
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <div>
                <h2 class="text-sm font-semibold text-slate-900">Ministry Announcements</h2>
                <p class="text-xs text-slate-500">Updates and rehearsal notices for the worship team</p>
              </div>
              ${isLeader ? `
                <button onclick="window.appActions.openAnnouncementModal()" class="btn btn-secondary btn-sm">
                  <i class="fa-solid fa-plus text-[10px]"></i> Post Update
                </button>
              ` : ""}
            </div>

            ${announcements.length === 0 ? `
              <div class="surface-card p-6 text-center">
                <p class="text-xs text-slate-500">No announcements posted yet.</p>
              </div>
            ` : `
              <div class="surface-card divide-y divide-slate-200">
                ${announcements.map((ann) => `
                  <div class="p-4 space-y-1.5">
                    <div class="flex items-start justify-between gap-2">
                      <div class="flex items-center gap-2">
                        ${ann.urgent ? `<span class="badge badge-rose">Urgent</span>` : ""}
                        <h3 class="text-sm font-semibold text-slate-900">${escapeHtml(ann.title || "Announcement")}</h3>
                      </div>
                      <div class="flex items-center gap-2 shrink-0">
                        <span class="text-[11px] text-slate-400">${formatRelativeTime(ann.createdAt)}</span>
                        ${isLeader ? `
                          <button onclick="window.appActions.deleteAnnouncement('${escapeHtml(ann.id)}')" class="text-slate-400 hover:text-rose-600 text-xs p-1" title="Delete Announcement">
                            <i class="fa-regular fa-trash-can"></i>
                          </button>
                        ` : ""}
                      </div>
                    </div>
                    <p class="text-xs text-slate-600 leading-relaxed whitespace-pre-line">${escapeHtml(ann.message || "")}</p>
                    <div class="pt-1 text-[11px] text-slate-400">
                      Posted by <span class="font-medium text-slate-600">${escapeHtml(ann.author || "Leader")}</span>
                    </div>
                  </div>
                `).join("")}
              </div>
            `}
          </div>
        </div>
      </div>
    </div>
  `;
}

export function renderServiceCardHtml(srv, state, isLeader) {
  const { currentUser } = state;
  const assigned = srv.assignments || [];
  const setlist = srv.songs || [];
  const rehearsal = srv.rehearsal || {};
  const mySlot = assigned.find((m) => m.uid === currentUser.uid);
  const status = srv.status || "Published";

  // Check if any assigned member has an unavailability conflict on srv.date
  const conflicts = [];
  if (srv.date) {
    for (const slot of assigned) {
      const memberObj = (state.teamMembers || []).find((u) => u.uid === slot.uid);
      const ranges = memberObj?.unavailableRanges || [];
      if (ranges.some((r) => srv.date >= r.startDate && srv.date <= r.endDate)) {
        conflicts.push(slot.name || memberObj?.fullName || "Member");
      }
    }
  }

  return `
    <div class="surface-card overflow-hidden">
      <!-- Top Service Header -->
      <div class="p-4 sm:p-5 border-b border-slate-100 flex flex-col sm:flex-row sm:items-start justify-between gap-3">
        <div class="flex items-start gap-3.5">
          ${srv.coverImageUrl ? `
            <img src="${escapeHtml(srv.coverImageUrl)}" alt="${escapeHtml(srv.title)}" class="w-12 h-12 rounded-md object-cover border border-slate-200 shrink-0" />
          ` : `
            <div class="w-12 h-12 rounded-md bg-slate-50 border border-slate-200 flex flex-col items-center justify-center shrink-0 text-slate-700">
              <i class="fa-regular fa-calendar-days text-sm text-indigo-600"></i>
            </div>
          `}
          <div class="space-y-1">
            <div class="flex flex-wrap items-center gap-1.5">
              <span class="badge ${status === "Draft" ? "badge-amber" : "badge-emerald"}">${escapeHtml(status)}</span>
              ${mySlot ? `
                <span class="badge badge-indigo">
                  <i class="fa-solid fa-check text-[9px]"></i> Assigned: ${escapeHtml(mySlot.role)}
                </span>
              ` : ""}
              ${conflicts.length > 0 ? `
                <span class="badge badge-rose" title="Unavailable member assigned: ${escapeHtml(conflicts.join(', '))}">
                  <i class="fa-solid fa-triangle-exclamation text-[9px]"></i> ${conflicts.length} Conflict${conflicts.length > 1 ? "s" : ""}
                </span>
              ` : ""}
            </div>
            <h3 class="text-base font-semibold text-slate-900">${escapeHtml(srv.title || "Worship Service")}</h3>
            <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
              <span><i class="fa-regular fa-calendar mr-1 text-slate-400"></i>${formatFriendlyDate(srv.date)}</span>
              ${srv.start ? `<span><i class="fa-regular fa-clock mr-1 text-slate-400"></i>${formatFriendlyTime(srv.start)}${srv.end ? ` - ${formatFriendlyTime(srv.end)}` : ""}</span>` : ""}
              ${srv.venue ? `<span><i class="fa-solid fa-location-dot mr-1 text-slate-400"></i>${escapeHtml(srv.venue)}</span>` : ""}
              ${rehearsal.date ? `
                <span class="text-amber-700 font-medium">
                  <i class="fa-solid fa-drum mr-1"></i>Rehearsal: ${formatFriendlyDate(rehearsal.date)} ${formatFriendlyTime(rehearsal.start || "")}
                </span>
              ` : ""}
            </div>
          </div>
        </div>

        <div class="flex items-center gap-1.5 shrink-0">
          ${mySlot ? `
            <button onclick="window.appActions.openSubstitutionModal('${escapeHtml(srv.id)}')" class="btn btn-secondary btn-sm" title="Request a substitute for your slot">
              <i class="fa-solid fa-arrows-rotate text-[11px]"></i> Request Sub
            </button>
          ` : ""}
          ${isLeader ? `
            <button onclick="window.appActions.openServiceModal('${escapeHtml(srv.id)}')" class="btn btn-secondary btn-sm" title="Edit Service & Lineup">
              <i class="fa-solid fa-pen text-[11px]"></i> Edit
            </button>
            <button onclick="window.appActions.deleteService('${escapeHtml(srv.id)}')" class="btn btn-danger btn-sm" title="Delete Service">
              <i class="fa-regular fa-trash-can text-[11px]"></i>
            </button>
          ` : ""}
        </div>
      </div>

      ${rehearsal.notes ? `
        <div class="px-4 sm:px-5 py-2.5 bg-slate-50 border-b border-slate-100 text-xs text-slate-600">
          <span class="font-semibold text-slate-700 mr-1">Notes:</span> ${escapeHtml(rehearsal.notes)}
        </div>
      ` : ""}

      <!-- Two-Column Body: Lineup Assignments & Song Setlist -->
      <div class="p-4 sm:p-5 grid grid-cols-1 md:grid-cols-2 gap-4 bg-white">
        <!-- Assigned Team Lineup -->
        <div class="border border-slate-200 rounded-md p-3.5 bg-slate-50/50">
          <div class="flex items-center justify-between mb-2.5">
            <span class="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
              Team Lineup (${assigned.length})
            </span>
          </div>
          ${assigned.length === 0 ? `
            <p class="text-xs text-slate-400 italic py-2">No members assigned yet.</p>
          ` : `
            <div class="divide-y divide-slate-200/70">
              ${assigned.map((slot) => {
                const isMe = slot.uid === currentUser.uid;
                const memberObj = (state.teamMembers || []).find((u) => u.uid === slot.uid);
                const isConflict = srv.date && memberObj && Array.isArray(memberObj.unavailableRanges) &&
                  memberObj.unavailableRanges.some((r) => srv.date >= r.startDate && srv.date <= r.endDate);
                return `
                  <div class="py-1.5 flex items-center justify-between gap-2 text-xs">
                    <span class="text-slate-500 font-medium">${escapeHtml(slot.role || "Member")}</span>
                    <div class="flex items-center gap-1.5">
                      ${isConflict ? `<i class="fa-solid fa-triangle-exclamation text-rose-600 text-[11px]" title="Marked unavailable on this date"></i>` : ""}
                      <span class="font-semibold ${isMe ? "text-indigo-700" : "text-slate-800"}">
                        ${escapeHtml(slot.name || "Unassigned")}${isMe ? " (You)" : ""}
                      </span>
                    </div>
                  </div>
                `;
              }).join("")}
            </div>
          `}
        </div>

        <!-- Worship Song Setlist -->
        <div class="border border-slate-200 rounded-md p-3.5 bg-slate-50/50">
          <div class="flex items-center justify-between mb-2.5">
            <span class="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
              Song Lineup (${setlist.length})
            </span>
          </div>
          ${setlist.length === 0 ? `
            <p class="text-xs text-slate-400 italic py-2">No songs added to setlist yet.</p>
          ` : `
            <div class="divide-y divide-slate-200/70">
              ${setlist.map((songItem, idx) => {
                const songTitle = typeof songItem === "string" ? songItem : (songItem.title || "Untitled");
                const matchedSong = (state.songs || []).find(
                  (s) => (s.title || "").trim().toLowerCase() === String(songTitle).trim().toLowerCase()
                );
                const displayKey = matchedSong?.key || songItem?.key || "";
                return `
                  <button onclick="window.appActions.openSongByTitle('${escapeHtml(songTitle)}')"
                    class="w-full text-left py-1.5 flex items-center justify-between gap-2 text-xs hover:bg-slate-100/80 px-1.5 -mx-1.5 rounded transition group">
                    <span class="truncate font-medium text-slate-800 group-hover:text-indigo-700">
                      <span class="text-slate-400 font-mono mr-1.5">${idx + 1}.</span>${escapeHtml(songTitle)}
                    </span>
                    <div class="flex items-center gap-1.5 shrink-0">
                      ${displayKey ? `<span class="badge badge-indigo font-mono text-[10px]">${escapeHtml(displayKey)}</span>` : ""}
                      <i class="fa-solid fa-arrow-up-right-from-square text-[10px] text-slate-400 group-hover:text-indigo-600"></i>
                    </div>
                  </button>
                `;
              }).join("")}
            </div>
          `}
        </div>
      </div>
    </div>
  `;
}

export function renderScheduleView(state) {
  const { currentUser, services, substitutions, teamMembers } = state;
  const isLeader = hasLeaderPrivileges(currentUser);
  const myUnavailability = currentUser?.unavailableRanges || [];

  return `
    <div class="space-y-6 animate-fade-in">
      <!-- Page Header -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 class="text-xl font-semibold text-slate-900">Schedule, Lineups & Availability</h1>
          <p class="text-xs text-slate-500 mt-0.5">Manage worship service dates, rehearsal schedules, role assignments, and member availability.</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button onclick="window.appActions.openAvailabilityModal()" class="btn btn-secondary">
            <i class="fa-regular fa-calendar-xmark text-slate-500"></i>
            My Unavailability (${myUnavailability.length})
          </button>
          ${isLeader ? `
            <button onclick="window.appActions.openServiceModal()" class="btn btn-primary">
              <i class="fa-solid fa-plus text-xs"></i> New Service Schedule
            </button>
          ` : ""}
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <!-- Left 8 Cols: Service Schedule List -->
        <div class="lg:col-span-8 space-y-4">
          ${services.length === 0 ? `
            <div class="surface-card p-10 text-center">
              <div class="w-10 h-10 rounded-md bg-slate-100 border border-slate-200 flex items-center justify-center mx-auto mb-3 text-slate-400">
                <i class="fa-regular fa-calendar-plus"></i>
              </div>
              <h3 class="text-sm font-semibold text-slate-900">No services on the calendar</h3>
              <p class="text-xs text-slate-500 mt-1 max-w-md mx-auto">Schedule upcoming Sunday services, youth nights, or special rehearsals to coordinate your worship team lineup.</p>
              ${isLeader ? `
                <button onclick="window.appActions.openServiceModal()" class="mt-4 btn btn-primary btn-sm">
                  <i class="fa-solid fa-plus text-[10px]"></i> Schedule First Service
                </button>
              ` : ""}
            </div>
          ` : services.map((srv) => renderServiceCardHtml(srv, state, isLeader)).join("")}
        </div>

        <!-- Right 4 Cols: Substitution Requests & Member Availability Log -->
        <div class="lg:col-span-4 space-y-5">
          <!-- Substitution Requests Card -->
          <div class="surface-card overflow-hidden">
            <div class="px-4 py-3 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
              <h3 class="text-xs font-semibold uppercase tracking-wider text-slate-700">Substitution Requests</h3>
              <span class="badge badge-neutral">${substitutions.length}</span>
            </div>
            <div class="p-4">
              ${substitutions.length === 0 ? `
                <p class="text-xs text-slate-500">No substitution requests submitted.</p>
              ` : `
                <div class="divide-y divide-slate-100 -my-2">
                  ${substitutions.slice(0, 8).map((sub) => {
                    const badgeClass =
                      sub.status === "Approved" || sub.status === "Accepted"
                        ? "badge-emerald"
                        : sub.status === "Declined" || sub.status === "Cancelled"
                        ? "badge-rose"
                        : "badge-amber";
                    return `
                      <div class="py-3 space-y-1.5">
                        <div class="flex items-center justify-between gap-2">
                          <span class="text-xs font-semibold text-slate-900">${escapeHtml(sub.requesterName || "Member")}</span>
                          <span class="badge ${badgeClass}">${escapeHtml(sub.status || "Pending")}</span>
                        </div>
                        <div class="text-xs text-slate-600">
                          Service: <strong class="text-slate-800">${escapeHtml(sub.serviceTitle || "Service")}</strong>
                        </div>
                        <div class="text-xs text-slate-500 italic">"${escapeHtml(sub.reason || "")}"</div>
                        ${sub.substituteName ? `
                          <div class="text-[11px] text-indigo-700 font-medium">Proposed Sub: ${escapeHtml(sub.substituteName)}</div>
                        ` : ""}
                        ${isLeader && (sub.status === "Pending" || sub.status === "Substitute Accepted") ? `
                          <div class="flex items-center gap-2 pt-1">
                            <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Approved')" class="btn btn-primary btn-sm">Approve</button>
                            <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Declined')" class="btn btn-danger btn-sm">Decline</button>
                          </div>
                        ` : ""}
                      </div>
                    `;
                  }).join("")}
                </div>
              `}
            </div>
          </div>

          <!-- Team Availability / Unavailability Dates Card -->
          <div class="surface-card overflow-hidden">
            <div class="px-4 py-3 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
              <h3 class="text-xs font-semibold uppercase tracking-wider text-slate-700">Team Unavailability</h3>
              <button onclick="window.appActions.openAvailabilityModal()" class="text-xs font-medium text-indigo-700 hover:underline">
                Edit Mine
              </button>
            </div>
            <div class="p-4">
              ${teamMembers.filter((m) => (m.unavailableRanges || []).length > 0).length === 0 ? `
                <div class="flex items-center gap-2 text-xs text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-md px-3 py-2">
                  <i class="fa-solid fa-circle-check"></i>
                  <span>All members are currently marked available.</span>
                </div>
              ` : `
                <div class="divide-y divide-slate-100 -my-2">
                  ${teamMembers
                    .filter((m) => (m.unavailableRanges || []).length > 0)
                    .map((m) => `
                      <div class="py-2.5 space-y-1.5">
                        <div class="flex items-center justify-between">
                          <span class="text-xs font-semibold text-slate-800">${escapeHtml(m.fullName || m.name || "Member")}</span>
                          <span class="text-[11px] text-slate-400">${escapeHtml(m.ministryRole || "Member")}</span>
                        </div>
                        <div class="flex flex-wrap gap-1">
                          ${(m.unavailableRanges || []).map((r) => `
                            <span class="badge badge-rose text-[10px]">
                              ${escapeHtml(r.startDate)}${r.endDate && r.endDate !== r.startDate ? ` to ${escapeHtml(r.endDate)}` : ""}
                              ${r.reason ? ` (${escapeHtml(r.reason)})` : ""}
                            </span>
                          `).join("")}
                        </div>
                      </div>
                    `).join("")}
                </div>
              `}
            </div>
          </div>
        </div>
      </div>
    </div>
  `;
}

export function renderSongsView(state) {
  const { currentUser, songs, songSearchQuery } = state;
  const isLeader = hasLeaderPrivileges(currentUser);
  const query = (songSearchQuery || "").trim().toLowerCase();

  const filteredSongs = songs.filter((s) => {
    if (!query) return true;
    return (
      (s.title || "").toLowerCase().includes(query) ||
      (s.artist || "").toLowerCase().includes(query) ||
      (s.key || "").toLowerCase().includes(query) ||
      (s.lyrics || "").toLowerCase().includes(query) ||
      (s.sheet || "").toLowerCase().includes(query)
    );
  });

  return `
    <div class="space-y-6 animate-fade-in">
      <!-- Page Header -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 class="text-xl font-semibold text-slate-900">Song Lineup & Chord Library</h1>
          <p class="text-xs text-slate-500 mt-0.5">View lyrics and chord charts, transpose keys in real time, and practice with the built-in metronome.</p>
        </div>
        ${isLeader ? `
          <button onclick="window.appActions.openSongEditorModal()" class="btn btn-primary">
            <i class="fa-solid fa-plus text-xs"></i> Add Song to Library
          </button>
        ` : ""}
      </div>

      <!-- Search & Filter Bar -->
      <div class="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        <div class="relative flex-1 max-w-md">
          <i class="fa-solid fa-magnifying-glass absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-xs"></i>
          <input
            id="song-search-input"
            type="text"
            value="${escapeHtml(songSearchQuery || "")}"
            oninput="window.appActions.setSongSearch(this.value)"
            placeholder="Filter by song title, artist, key, or lyrics..."
            class="form-input pl-8 pr-8"
          />
          ${songSearchQuery ? `
            <button onclick="window.appActions.setSongSearch('')" class="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 text-xs">
              <i class="fa-solid fa-xmark"></i>
            </button>
          ` : ""}
        </div>
        <div class="text-xs text-slate-500">
          Showing <strong class="text-slate-800">${filteredSongs.length}</strong> of ${songs.length} songs
        </div>
      </div>

      ${filteredSongs.length === 0 ? `
        <div class="surface-card p-10 text-center">
          <div class="w-10 h-10 rounded-md bg-slate-100 border border-slate-200 flex items-center justify-center mx-auto mb-3 text-slate-400">
            <i class="fa-solid fa-music"></i>
          </div>
          <h3 class="text-sm font-semibold text-slate-900">No matching songs found</h3>
          <p class="text-xs text-slate-500 mt-1">Add worship songs with chord sheets, original keys, BPM tempo, and YouTube reference links.</p>
          ${isLeader ? `
            <button onclick="window.appActions.openSongEditorModal()" class="mt-4 btn btn-primary btn-sm">
              <i class="fa-solid fa-plus text-[10px]"></i> Add First Song
            </button>
          ` : ""}
        </div>
      ` : `
        <div class="surface-card overflow-hidden">
          <div class="overflow-x-auto">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Song Title & Artist</th>
                  <th>Original Key</th>
                  <th>Tempo</th>
                  <th>Chart / Lyrics</th>
                  <th>Reference</th>
                  <th class="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                ${filteredSongs.map((song) => {
                  const hasChords = Boolean((song.sheet && song.sheet.trim()) || (song.lyrics && song.lyrics.trim()));
                  const hasYt = Boolean(song.referenceUrl && song.referenceUrl.trim());
                  return `
                    <tr class="cursor-pointer" onclick="window.appActions.openSongStageModal('${escapeHtml(song.id)}')">
                      <td>
                        <div class="font-semibold text-slate-900 hover:text-indigo-700">${escapeHtml(song.title || "Untitled Song")}</div>
                        <div class="text-xs text-slate-500">${escapeHtml(song.artist || "Unknown Artist")}</div>
                      </td>
                      <td>
                        <span class="badge badge-indigo font-mono">${escapeHtml(song.key || "C")}</span>
                      </td>
                      <td>
                        ${song.bpm ? `<span class="text-xs font-mono text-slate-700">${escapeHtml(song.bpm)} BPM</span>` : `<span class="text-xs text-slate-400">&mdash;</span>`}
                      </td>
                      <td>
                        ${hasChords
                          ? `<span class="badge badge-emerald"><i class="fa-solid fa-check text-[9px]"></i> Available</span>`
                          : `<span class="badge badge-neutral">No Chart</span>`}
                      </td>
                      <td>
                        ${hasYt
                          ? `<span class="badge badge-rose"><i class="fa-brands fa-youtube text-[10px]"></i> YouTube</span>`
                          : `<span class="text-xs text-slate-400">&mdash;</span>`}
                      </td>
                      <td class="text-right" onclick="event.stopPropagation()">
                        <div class="inline-flex items-center justify-end gap-1.5">
                          <button onclick="window.appActions.openSongStageModal('${escapeHtml(song.id)}')" class="btn btn-secondary btn-sm">
                            <i class="fa-solid fa-sliders text-[10px]"></i> Stage View
                          </button>
                          ${isLeader ? `
                            <button onclick="window.appActions.openSongEditorModal('${escapeHtml(song.id)}')" class="btn btn-ghost btn-sm" title="Edit Song">
                              <i class="fa-solid fa-pen text-xs"></i>
                            </button>
                            <button onclick="window.appActions.deleteSong('${escapeHtml(song.id)}')" class="btn btn-ghost btn-sm text-rose-600 hover:bg-rose-50" title="Delete Song">
                              <i class="fa-regular fa-trash-can text-xs"></i>
                            </button>
                          ` : ""}
                        </div>
                      </td>
                    </tr>
                  `;
                }).join("")}
              </tbody>
            </table>
          </div>
        </div>
      `}
    </div>
  `;
}

export function renderRosterView(state) {
  const { currentUser, currentTeam, teamMembers, rejoinRequests } = state;
  const isPrimaryLeader = isWorshipLeader(currentUser.ministryRole, currentUser.role) || currentTeam?.leaderUid === currentUser.uid;
  const isLeader = hasLeaderPrivileges(currentUser);
  const pendingRejoins = rejoinRequests.filter((r) => r.status === "Pending");
  const teamCode = currentTeam?.teamCode || currentUser?.teamCode || "------";

  return `
    <div class="space-y-6 animate-fade-in">
      <!-- Page Header -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 class="text-xl font-semibold text-slate-900">Team Management & Roster</h1>
          <p class="text-xs text-slate-500 mt-0.5">Manage ministry members, assign musical & leadership roles, and review availability.</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button onclick="window.appActions.copyTeamCode()" class="btn btn-secondary">
            <i class="fa-solid fa-key text-slate-500 text-xs"></i>
            Invite Code: <span class="font-mono font-semibold text-slate-900">${escapeHtml(teamCode)}</span>
          </button>
          ${isPrimaryLeader ? `
            <button onclick="window.appActions.openTransferLeadershipModal()" class="btn btn-secondary">
              <i class="fa-solid fa-crown text-amber-600 text-xs"></i> Transfer Leadership
            </button>
          ` : ""}
        </div>
      </div>

      <!-- Pending Rejoin Requests (If any) -->
      ${isLeader && pendingRejoins.length > 0 ? `
        <div class="surface-card border-amber-200 overflow-hidden">
          <div class="px-4 py-2.5 bg-amber-50 border-b border-amber-200 flex items-center justify-between">
            <span class="text-xs font-semibold text-amber-900">Pending Rejoin Requests (${pendingRejoins.length})</span>
          </div>
          <div class="divide-y divide-slate-100">
            ${pendingRejoins.map((req) => `
              <div class="p-3.5 flex items-center justify-between gap-4">
                <div>
                  <div class="text-sm font-semibold text-slate-900">${escapeHtml(req.userName || "Former Member")}</div>
                  <div class="text-xs text-slate-500">Requested to rejoin the ministry team</div>
                </div>
                <div class="flex items-center gap-2">
                  <button onclick="window.appActions.handleRejoinRequest('${escapeHtml(req.id)}', true)" class="btn btn-primary btn-sm">Approve</button>
                  <button onclick="window.appActions.handleRejoinRequest('${escapeHtml(req.id)}', false)" class="btn btn-danger btn-sm">Decline</button>
                </div>
              </div>
            `).join("")}
          </div>
        </div>
      ` : ""}

      <!-- Structured Team Directory Table -->
      <div class="surface-card overflow-hidden">
        <div class="overflow-x-auto">
          <table class="data-table">
            <thead>
              <tr>
                <th>Member</th>
                <th>Authority / Role</th>
                <th>Availability Status</th>
                ${isLeader ? `<th class="text-right">Role Assignment & Actions</th>` : ""}
              </tr>
            </thead>
            <tbody>
              ${teamMembers.map((member) => {
                const isMe = member.uid === currentUser.uid;
                const memberIsPrimaryLeader = isWorshipLeader(member.ministryRole, member.role) || currentTeam?.leaderUid === member.uid;
                const unavailCount = (member.unavailableRanges || []).length;
                const displayName = member.fullName || member.name || member.username || "Member";

                return `
                  <tr>
                    <td>
                      <div class="flex items-center gap-3">
                        ${getAvatarHtml(member, "w-9 h-9", "text-xs")}
                        <div class="min-w-0">
                          <div class="font-semibold text-slate-900 truncate">
                            ${escapeHtml(displayName)}
                            ${isMe ? `<span class="text-xs font-normal text-slate-400 ml-1">(You)</span>` : ""}
                          </div>
                          <div class="text-xs text-slate-500 truncate">${escapeHtml(member.email || "")}</div>
                        </div>
                      </div>
                    </td>
                    <td>
                      ${getRoleBadgeHtml(member)}
                    </td>
                    <td>
                      ${unavailCount === 0
                        ? `<span class="badge badge-emerald"><i class="fa-solid fa-check text-[9px]"></i> Available</span>`
                        : `<span class="badge badge-amber"><i class="fa-regular fa-calendar-xmark text-[9px]"></i> ${unavailCount} Date Range${unavailCount > 1 ? "s" : ""} Unavailable</span>`
                      }
                    </td>
                    ${isLeader ? `
                      <td class="text-right">
                        ${!memberIsPrimaryLeader ? `
                          <div class="inline-flex items-center justify-end gap-2">
                            <select
                              onchange="window.appActions.assignMemberRole('${escapeHtml(member.uid)}', this.value)"
                              class="form-select py-1 px-2.5 text-xs w-44"
                            >
                              ${DEFAULT_MINISTRY_ROLES.filter((r) => r !== "Worship Leader").map((r) => `
                                <option value="${escapeHtml(r)}" ${member.ministryRole === r ? "selected" : ""}>${escapeHtml(r)}</option>
                              `).join("")}
                            </select>
                            ${!isMe ? `
                              <button onclick="window.appActions.removeMemberFromTeam('${escapeHtml(member.uid)}', '${escapeHtml(displayName)}')"
                                class="btn btn-danger btn-sm" title="Remove from Team">
                                <i class="fa-solid fa-user-minus text-[11px]"></i>
                              </button>
                            ` : ""}
                          </div>
                        ` : `
                          <span class="text-xs text-slate-400 italic">Primary Leader</span>
                        `}
                      </td>
                    ` : ""}
                  </tr>
                `;
              }).join("")}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `;
}

export function renderChatView(state) {
  const { currentUser, messages, chatReplyTarget, chatEditingMessage } = state;

  return `
    <div class="surface-card flex flex-col h-[calc(100vh-8.5rem)] min-h-[520px] overflow-hidden animate-fade-in">
      <!-- Chat Header -->
      <div class="px-5 py-3.5 border-b border-slate-200 flex items-center justify-between bg-white">
        <div class="flex items-center gap-2.5">
          <div class="w-8 h-8 rounded-md bg-slate-100 border border-slate-200 flex items-center justify-center text-slate-700 text-xs">
            <i class="fa-regular fa-comments"></i>
          </div>
          <div>
            <h2 class="text-sm font-semibold text-slate-900">Ministry Team Chat</h2>
            <p class="text-[11px] text-slate-500">Real-time coordination synced across web and mobile</p>
          </div>
        </div>
        <span class="badge badge-emerald">
          <span class="w-1.5 h-1.5 rounded-full bg-emerald-600"></span> Connected
        </span>
      </div>

      <!-- Messages Scroll Container -->
      <div id="chat-messages-scroll" class="flex-1 overflow-y-auto p-5 space-y-3.5 bg-[#F8FAFC]">
        ${messages.length === 0 ? `
          <div class="h-full flex flex-col items-center justify-center text-center py-12">
            <div class="w-10 h-10 rounded-md bg-white border border-slate-200 flex items-center justify-center mb-2.5 text-slate-400">
              <i class="fa-regular fa-comment-dots"></i>
            </div>
            <p class="text-sm font-medium text-slate-700">No messages yet</p>
            <p class="text-xs text-slate-400 mt-0.5">Start a conversation with your worship team.</p>
          </div>
        ` : messages.map((msg) => {
          const isMe = msg.senderUid === currentUser.uid;
          const isDeleted = Boolean(msg.isDeleted);

          return `
            <div class="flex items-start gap-2.5 ${isMe ? "flex-row-reverse" : ""}">
              ${getAvatarHtml({ fullName: msg.senderName, photoUri: msg.senderPhotoUri }, "w-7 h-7", "text-[10px]")}
              <div class="max-w-[78%] sm:max-w-[65%] flex flex-col ${isMe ? "items-end" : "items-start"}">
                <div class="flex items-center gap-1.5 mb-1 px-0.5">
                  <span class="text-[11px] font-semibold text-slate-700">${escapeHtml(isMe ? "You" : (msg.senderName || "Member"))}</span>
                  ${msg.senderRole ? `<span class="text-[10px] text-slate-400">&middot; ${escapeHtml(msg.senderRole)}</span>` : ""}
                  <span class="text-[10px] text-slate-400">${formatRelativeTime(msg.timestamp)}</span>
                </div>

                <div class="group relative rounded-lg px-3.5 py-2.5 text-xs leading-relaxed ${
                  isDeleted
                    ? "bg-slate-100 border border-slate-200 text-slate-400 italic"
                    : isMe
                    ? "bg-slate-900 text-white"
                    : "bg-white border border-slate-200 text-slate-800 shadow-2xs"
                }">
                  ${!isDeleted && msg.replyToId ? `
                    <div class="mb-2 pl-2.5 py-1 border-l-2 ${isMe ? "border-indigo-400 bg-white/10 text-slate-200" : "border-indigo-600 bg-slate-50 text-slate-600"} rounded-r text-[11px]">
                      <div class="font-semibold">${escapeHtml(msg.replyToSender || "Member")}</div>
                      <div class="truncate opacity-90">${escapeHtml(msg.replyToText || "Photo")}</div>
                    </div>
                  ` : ""}

                  ${!isDeleted && msg.mediaUrl ? `
                    <a href="${escapeHtml(msg.mediaUrl)}" target="_blank" rel="noopener" class="block mb-2">
                      <img src="${escapeHtml(msg.mediaUrl)}" alt="Attachment" class="max-h-60 rounded border border-slate-200 object-cover" />
                    </a>
                  ` : ""}

                  <div class="whitespace-pre-wrap break-words">${isDeleted ? "This message was unsent." : escapeHtml(msg.text || "")}</div>
                  ${!isDeleted && msg.isEdited ? `<div class="text-[10px] opacity-65 mt-1">(edited)</div>` : ""}

                  ${!isDeleted ? `
                    <div class="hidden group-hover:flex items-center gap-1 absolute -top-3 ${isMe ? "left-2" : "right-2"} bg-white border border-slate-200 rounded shadow-xs px-1.5 py-0.5 text-slate-600">
                      <button onclick="window.appActions.setChatReply('${escapeHtml(msg.id)}')" class="hover:text-indigo-600 p-1 text-[10px]" title="Reply">
                        <i class="fa-solid fa-reply"></i>
                      </button>
                      ${isMe ? `
                        <button onclick="window.appActions.startEditChatMessage('${escapeHtml(msg.id)}')" class="hover:text-amber-600 p-1 text-[10px]" title="Edit">
                          <i class="fa-solid fa-pen"></i>
                        </button>
                        <button onclick="window.appActions.retractChatMessage('${escapeHtml(msg.id)}')" class="hover:text-rose-600 p-1 text-[10px]" title="Unsend">
                          <i class="fa-regular fa-trash-can"></i>
                        </button>
                      ` : ""}
                    </div>
                  ` : ""}
                </div>
              </div>
            </div>
          `;
        }).join("")}
      </div>

      <!-- Reply or Edit Context Banner -->
      ${chatReplyTarget ? `
        <div class="px-4 py-2 bg-indigo-50 border-t border-indigo-100 flex items-center justify-between text-xs">
          <div class="truncate text-indigo-950">
            <span class="font-semibold text-indigo-700"><i class="fa-solid fa-reply mr-1"></i>Replying to ${escapeHtml(chatReplyTarget.senderName)}:</span>
            <span class="text-slate-600 ml-1">${escapeHtml(chatReplyTarget.text || "Photo")}</span>
          </div>
          <button onclick="window.appActions.clearChatReply()" class="text-slate-400 hover:text-slate-700 p-1">
            <i class="fa-solid fa-xmark"></i>
          </button>
        </div>
      ` : ""}

      ${chatEditingMessage ? `
        <div class="px-4 py-2 bg-amber-50 border-t border-amber-200 flex items-center justify-between text-xs">
          <div class="truncate text-amber-900">
            <span class="font-semibold"><i class="fa-solid fa-pen mr-1"></i>Editing message:</span>
            <span class="ml-1">${escapeHtml(chatEditingMessage.text)}</span>
          </div>
          <button onclick="window.appActions.clearChatEdit()" class="text-slate-400 hover:text-slate-700 p-1">
            <i class="fa-solid fa-xmark"></i>
          </button>
        </div>
      ` : ""}

      <!-- Message Composer -->
      <form onsubmit="window.appActions.sendChatMessage(event)" class="p-3 bg-white border-t border-slate-200 flex items-center gap-2">
        <label class="btn btn-secondary btn-sm cursor-pointer shrink-0" title="Send Image">
          <i class="fa-regular fa-image text-slate-600"></i>
          <input id="chat-photo-input" type="file" accept="image/*" class="hidden" onchange="window.appActions.sendChatPhoto(this)" />
        </label>
        <input
          id="chat-message-input"
          type="text"
          value="${escapeHtml(chatEditingMessage ? chatEditingMessage.text : "")}"
          placeholder="Write a message to the team..."
          class="form-input flex-1"
          autocomplete="off"
        />
        <button type="submit" class="btn btn-primary shrink-0">
          <i class="fa-solid fa-paper-plane text-xs"></i> Send
        </button>
      </form>
    </div>
  `;
}

export function renderProfileView(state) {
  const { currentUser, currentTeam } = state;
  const isPrimaryLeader = isWorshipLeader(currentUser.ministryRole, currentUser.role) || currentTeam?.leaderUid === currentUser.uid;
  const displayName = currentUser.fullName || currentUser.name || "Member";
  const teamDisplayName = currentTeam?.teamName || currentUser?.teamName || "None";

  return `
    <div class="max-w-2xl mx-auto space-y-6 animate-fade-in">
      <!-- Page Header -->
      <div class="pb-4 border-b border-slate-200">
        <h1 class="text-xl font-semibold text-slate-900">Account & Ministry Profile</h1>
        <p class="text-xs text-slate-500 mt-0.5">Update your personal details, profile photo, and ministry instrument/role.</p>
      </div>

      <!-- Profile Card -->
      <div class="surface-card p-6 space-y-6">
        <div class="flex flex-col sm:flex-row items-start sm:items-center gap-4 pb-6 border-b border-slate-200">
          <div class="relative">
            ${getAvatarHtml(currentUser, "w-16 h-16", "text-lg")}
            <label class="absolute -bottom-1 -right-1 w-7 h-7 rounded-full bg-white border border-slate-300 shadow-xs flex items-center justify-center text-slate-700 hover:bg-slate-50 cursor-pointer" title="Upload Avatar">
              <i class="fa-solid fa-camera text-[11px]"></i>
              <input id="profile-avatar-input" type="file" accept="image/*" class="hidden" onchange="window.appActions.uploadProfileAvatar(this)" />
            </label>
          </div>
          <div class="space-y-1">
            <h2 class="text-base font-semibold text-slate-900">${escapeHtml(displayName)}</h2>
            <p class="text-xs text-slate-500">${escapeHtml(currentUser.email || "")}</p>
            <div class="pt-1">${getRoleBadgeHtml(currentUser)}</div>
          </div>
        </div>

        <form onsubmit="window.appActions.saveProfileChanges(event)" class="space-y-4">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label class="form-label">Full Name</label>
              <input
                id="profile-name-input"
                type="text"
                required
                maxlength="60"
                value="${escapeHtml(displayName)}"
                class="form-input"
              />
            </div>
            <div>
              <label class="form-label">Phone Number (Optional)</label>
              <input
                id="profile-phone-input"
                type="text"
                maxlength="25"
                value="${escapeHtml(currentUser.phone || "")}"
                placeholder="+63 9XX XXX XXXX"
                class="form-input"
              />
            </div>
          </div>

          <div>
            <label class="form-label">Primary Ministry Role / Instrument</label>
            <select
              id="profile-role-select"
              ${isPrimaryLeader ? "disabled" : ""}
              class="form-select disabled:bg-slate-100 disabled:text-slate-500"
            >
              ${DEFAULT_MINISTRY_ROLES.map((r) => `
                <option value="${escapeHtml(r)}" ${currentUser.ministryRole === r ? "selected" : ""}>${escapeHtml(r)}</option>
              `).join("")}
            </select>
            ${isPrimaryLeader ? `
              <p class="text-[11px] text-slate-500 mt-1">Primary Worship Leaders can transfer leadership from the Team Roster page.</p>
            ` : ""}
          </div>

          <div class="pt-2 flex justify-end">
            <button type="submit" class="btn btn-primary">
              Save Profile Changes
            </button>
          </div>
        </form>
      </div>

      <!-- Workspace Membership & Session Controls -->
      <div class="surface-card p-6 space-y-4">
        <div>
          <h3 class="text-sm font-semibold text-slate-900">Workspace & Session</h3>
          <p class="text-xs text-slate-500 mt-0.5">Current team: <strong class="text-slate-800">${escapeHtml(teamDisplayName)}</strong></p>
        </div>
        <div class="flex flex-wrap items-center gap-3 pt-1">
          <button onclick="window.appActions.leaveCurrentTeam()" class="btn btn-danger">
            <i class="fa-solid fa-person-walking-arrow-right text-xs"></i> Leave Current Team
          </button>
          <button onclick="window.appActions.signOutUser()" class="btn btn-secondary">
            <i class="fa-solid fa-right-from-bracket text-xs"></i> Sign Out
          </button>
        </div>
      </div>
    </div>
  `;
}
