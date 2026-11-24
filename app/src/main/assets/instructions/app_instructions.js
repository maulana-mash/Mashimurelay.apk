/**
 * Mashimurelay Instructions & Encyclopedia Controller for Android
 */
(function() {
  'use strict';

  let activeEncycloCategory = 'all';
  let activeEncycloQuery = '';

  document.addEventListener('DOMContentLoaded', () => {
    initCloseButton();
    initTabs();
    initEncyclopediaFilters();
    initEncyclopediaSearch();

    // Inisialisasi video simulasi interaktif
    const mount = document.getElementById('simVideoPlayerMount');
    if (mount && window.simVideoPlayer) {
      window.simVideoPlayer.init(mount);
    }

    // Cek deep-link ?video=<videoId> dari Modul Praktikum Android
    let requestedVideoId = null;
    try {
      const params = new URLSearchParams(window.location.search);
      requestedVideoId = params.get('video');
    } catch (_) {}

    if (window.simVideoPlayer) {
      if (requestedVideoId && typeof window.simVideoPlayer.loadVideo === 'function') {
        const videoTab = document.querySelector('.inst-tab[data-tab="tabVideoSim"]');
        if (videoTab) videoTab.click();
        window.simVideoPlayer.loadVideo(requestedVideoId);
      } else {
        window.simVideoPlayer.renderFrame();
      }
      window.simVideoPlayer.play();
    }
  });

  function initCloseButton() {
    const btnClose = document.getElementById('btnCloseInstructions');
    if (btnClose) {
      btnClose.addEventListener('click', () => {
        stopEasterEggBgm(false);
        if (window.AndroidBridge && typeof window.AndroidBridge.closeDialog === 'function') {
          window.AndroidBridge.closeDialog();
        }
      });
    }
  }

  function initTabs() {
    const tabs = document.querySelectorAll('.inst-tab');
    tabs.forEach(tab => {
      tab.addEventListener('click', () => {
        tabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        try {
          tab.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
        } catch (_) {}

        const targetId = tab.getAttribute('data-tab');
        document.querySelectorAll('.inst-section').forEach(sec => {
          sec.classList.remove('active');
        });

        const targetSec = document.getElementById(targetId);
        if (targetSec) {
          targetSec.classList.add('active');
        }

        if (targetId === 'tabVideoSim' && window.simVideoPlayer) {
          stopEasterEggBgm(true);
          window.simVideoPlayer.renderFrame();
          window.simVideoPlayer.play();
        } else if (targetId === 'tabComponents') {
          renderEncyclopediaGallery();
        } else {
          stopEasterEggBgm(true);
        }
      });
    });
  }

  function initEncyclopediaFilters() {
    const filterContainer = document.getElementById('encycloFilters');
    if (!filterContainer) return;

    filterContainer.querySelectorAll('.encyclo-filter-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        filterContainer.querySelectorAll('.encyclo-filter-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        activeEncycloCategory = btn.getAttribute('data-cat') || 'all';
        renderEncyclopediaGallery();
      });
    });
  }

  function initEncyclopediaSearch() {
    const input = document.getElementById('encycloSearchInput');
    if (!input) return;

    input.addEventListener('input', (e) => {
      activeEncycloQuery = e.target.value;
      renderEncyclopediaGallery();
      if (activeEasterEggAudio && activeEasterEggAudio.paused) {
        activeEasterEggAudio.play().then(() => {
          startAudioFadeIn(activeEasterEggAudio);
        }).catch(() => {});
      }
    });
    input.addEventListener('keydown', (e) => {
      setTimeout(() => {
        if (activeEasterEggAudio && activeEasterEggAudio.paused) {
          activeEasterEggAudio.play().then(() => {
            startAudioFadeIn(activeEasterEggAudio);
          }).catch(() => {});
        }
      }, 20);
    });
  }

  // Easter Egg Background Audio System for Android
  let activeEasterEggAudio = null;
  let activeEasterEggTrack = null;
  let audioFadeInterval = null;

  function startAudioFadeIn(audio) {
    clearInterval(audioFadeInterval);
    let vol = audio.volume;
    audioFadeInterval = setInterval(() => {
      vol += 0.05;
      if (vol >= 0.65) {
        vol = 0.65;
        clearInterval(audioFadeInterval);
      }
      if (activeEasterEggAudio) activeEasterEggAudio.volume = vol;
    }, 80);
  }

  function playEasterEggBgm(trackUrl) {
    if (activeEasterEggTrack === trackUrl && activeEasterEggAudio && !activeEasterEggAudio.paused) {
      return;
    }
    stopEasterEggBgm(false);

    try {
      const audio = new Audio(trackUrl);
      audio.loop = true;
      audio.volume = 0;
      activeEasterEggAudio = audio;
      activeEasterEggTrack = trackUrl;

      const triggerPlayback = () => {
        if (!activeEasterEggAudio || activeEasterEggTrack !== trackUrl) return;
        const playPromise = activeEasterEggAudio.play();
        if (playPromise !== undefined) {
          playPromise.then(() => {
            startAudioFadeIn(activeEasterEggAudio);
          }).catch(err => {
            console.log('Easter egg audio awaiting user interaction gesture:', err);
          });
        }
      };

      triggerPlayback();

      const unlockOnGesture = () => {
        if (activeEasterEggAudio && activeEasterEggAudio.paused && activeEasterEggTrack === trackUrl) {
          triggerPlayback();
        }
        window.removeEventListener('pointerdown', unlockOnGesture);
        window.removeEventListener('touchstart', unlockOnGesture);
      };

      window.addEventListener('pointerdown', unlockOnGesture, { once: true });
      window.addEventListener('touchstart', unlockOnGesture, { once: true });
    } catch (e) {
      console.error('Easter egg audio error:', e);
    }
  }

  function stopEasterEggBgm(smooth = true) {
    clearInterval(audioFadeInterval);
    if (!activeEasterEggAudio) {
      activeEasterEggTrack = null;
      return;
    }

    const audioToStop = activeEasterEggAudio;
    activeEasterEggAudio = null;
    activeEasterEggTrack = null;

    if (!smooth) {
      try {
        audioToStop.pause();
        audioToStop.currentTime = 0;
      } catch (e) {}
      return;
    }

    try {
      let vol = audioToStop.volume;
      const fadeOutInterval = setInterval(() => {
        vol -= 0.1;
        if (vol <= 0) {
          vol = 0;
          clearInterval(fadeOutInterval);
          try {
            audioToStop.pause();
            audioToStop.currentTime = 0;
          } catch (e) {}
        } else {
          audioToStop.volume = vol;
        }
      }, 50);
    } catch (e) {
      try { audioToStop.pause(); } catch (_) {}
    }
  }
  window.stopEasterEggBgm = stopEasterEggBgm;

  const SECRET_DEV_TRIGGERS = new Set([
    'pesan-dev',
    'pesan dev',
    'pesan developer',
    'pesan mash',
    'pesan-mash',
    'pesan-mash-dev',
    'pesan mash dev'
  ]);

  const SECRET_LOVER_TRIGGERS = new Set([
    'zaamoll-secret',
    'zaamoll secret',
    'zaamoll-special',
    'zaamoll special',
    'special zaamoll',
    'special-zaamoll'
  ]);

  function attachEasterEggCardAudioTriggers(card) {
    const triggerAudio = () => {
      if (activeEasterEggAudio && activeEasterEggAudio.paused) {
        activeEasterEggAudio.play().then(() => {
          startAudioFadeIn(activeEasterEggAudio);
        }).catch(() => {});
      }
    };
    ['touchstart', 'pointerdown', 'click'].forEach(evt => {
      card.addEventListener(evt, triggerAudio, { passive: true });
    });
  }

  function createSpecialLoverEasterEggCard() {
    const card = document.createElement('div');
    card.className = 'encyclo-card encyclo-card-special';
    card.innerHTML = `
      <div class="encyclo-special-header">
        <div class="encyclo-special-header-row">
          <div class="encyclo-special-title">Pesan untuk Orang Tersayang</div>
          <div class="encyclo-special-badge">SPECIAL</div>
        </div>
        <div class="badges-row">
          <span class="encyclo-special-tag">Tag: <span>Lover</span></span>
          <span class="encyclo-special-archive-badge">Secret Archive</span>
        </div>
      </div>
      <div class="encyclo-special-collage-box">
        <div class="encyclo-special-collage-container">
          <img src="assets/icons/lover_collage.svg" alt="Lover Collage" onerror="this.src='../icons/lover_collage.svg'" />
        </div>
      </div>
      <div class="encyclo-special-partner-box">
        <div class="encyclo-special-partner-text">
          Maulana <span class="name-highlight">&ldquo;Mash&rdquo;</span> Henutama dan<br>Halimatuz <span class="name-highlight">&ldquo;Izza&rdquo;</span> Zahroh
        </div>
      </div>
      <div class="encyclo-special-msg-box">
        <div class="encyclo-special-msg-header">
          <span class="encyclo-special-msg-badge">Love Message</span>
        </div>
        <div class="encyclo-special-msg-content">
          Terima kasih sudah membersamai developer selama pembuatan dan pengembangan software ini. Terima kasih banyak atas tenaga, waktu, pikiran, dan supportnya, sengg. Maaf yaa, selalu ngerepotin kamu, bikin ngambek kamu, dan menghilang demi porject ini （*＾-＾*）. <span class="highlight-love">LOVE YOU BANYAKK SAYANGG ❤❤❤</span>
        </div>
      </div>
    `;
    attachEasterEggCardAudioTriggers(card);
    return card;
  }

  function createDevEasterEggCard() {
    const card = document.createElement('div');
    card.className = 'encyclo-card encyclo-card-secret';
    card.innerHTML = `
      <div class="encyclo-card-header">
        <div class="encyclo-icon-box encyclo-secret-icon-box">
          <img src="assets/icons/developer_avatar.png" alt="Developer Avatar" onerror="this.src='../icons/developer_avatar.png'" style="width:100%;height:100%;object-fit:cover;border-radius:12px;" />
        </div>
        <div class="encyclo-header-meta">
          <div class="encyclo-secret-title-row">
            <div class="encyclo-title">Pesan <span style="color: #38bdf8;">&ldquo;Mash&rdquo;</span> Developer</div>
            <div class="encyclo-secret-badge">SECRET</div>
          </div>
          <div class="encyclo-badges-row">
            <span class="encyclo-tag-badge">Tag: Message</span>
            <span class="encyclo-cat-badge">Secret Archive</span>
          </div>
        </div>
      </div>
      <div class="encyclo-desc">
        <div class="encyclo-secret-headline">Terima Kasih Semuanya, Selamat Telah Menemukan Rahasia Ini :D</div>
        <div style="font-size: 12px; line-height: 1.6; color: #cbd5e1; text-align: justify;">
          Saya di sini sebagai pengembang dari software Mashimurelay ini sangat berterimakasih kepada seluruh pengguna yang telah mau menggunakan dan memanfaatkan software ini untuk perkembangan pendidikan teknik di Indonesia. Terima kasih untuk Guru yang mau berbagi ilmu dan Murid yang mau menerima ilmu, serta Kalian yang sudah bekerja keras sampai saat ini. Tidak lupa juga untuk Antigravity yang sudah sangat membantu developer dalam pengembangan software ini dari awal sampai akhir. <strong style="color: #38bdf8;">TETAP SEMANGAT!!!</strong>
        </div>
      </div>
      <div class="encyclo-secret-motto">
        &ldquo;Do better than yesterday, be the best for today, and doing greatfull for tomorrow&rdquo;
      </div>
      <div class="encyclo-secret-meta">
        <div class="encyclo-secret-meta-row">
          <span class="encyclo-secret-meta-label">Tanggal</span>
          <span class="encyclo-secret-meta-val">23-10-2026</span>
        </div>
        <div class="encyclo-secret-meta-row">
          <span class="encyclo-secret-meta-label">Tujuan</span>
          <span class="encyclo-secret-meta-val">Skripsi developer (. ❛ ᴗ ❛.)</span>
        </div>
        <div class="encyclo-secret-meta-row">
          <span class="encyclo-secret-meta-label">Harapan</span>
          <span class="encyclo-secret-meta-val">Semoga kerja keras ini berguna dan bermanfaat untuk kita semua</span>
        </div>
      </div>
    `;
    attachEasterEggCardAudioTriggers(card);
    return card;
  }

  function renderEncyclopediaGallery() {
    const grid = document.getElementById('encycloGrid');
    if (!grid) return;
    grid.innerHTML = '';

    const compData = window.COMPONENTS_DATA || (typeof COMPONENTS_DATA !== 'undefined' ? COMPONENTS_DATA : null);
    if (!compData) {
      grid.innerHTML = '<div style="text-align:center; padding:20px; color:#94a3b8;">Memuat data komponen...</div>';
      return;
    }

    const query = activeEncycloQuery.toLowerCase().trim();
    let count = 0;
    const knowledgeBase = window.COMPONENT_ENCYCLOPEDIA_KNOWLEDGE || {};

    // Secret Easter Egg Trigger Check
    if (activeEncycloCategory === 'all' && SECRET_DEV_TRIGGERS.has(query)) {
      grid.appendChild(createDevEasterEggCard());
      count++;
      playEasterEggBgm('assets/sounds/secret_dev_bgm.m4a');
    } else if (activeEncycloCategory === 'all' && SECRET_LOVER_TRIGGERS.has(query)) {
      grid.appendChild(createSpecialLoverEasterEggCard());
      count++;
      playEasterEggBgm('assets/sounds/special_lover_bgm.m4a');
    } else {
      stopEasterEggBgm(true);
    }

    for (const [k, v] of Object.entries(compData)) {
      const cat = v.category || (v.info && v.info.category) || 'others';
      const name = (window.COMPONENT_DISPLAY_NAMES && window.COMPONENT_DISPLAY_NAMES[k]) || v.name || (v.info && v.info.name) || k;
      const tagType = v.tagType || (v.info && v.info.tagType) || '-';
      const knowledge = knowledgeBase[k] || {};
      const fullName = knowledge.name || name;

      // Filter kategori
      if (activeEncycloCategory !== 'all' && cat !== activeEncycloCategory) continue;

      const desc = knowledge.desc || (v.info && v.info.description) || `Komponen kelistrikan industri kategori ${cat}.`;
      const how = knowledge.how || 'Mengalirkan arus dan sinyal kontrol pada diagram skematik otomasi.';
      const ioList = Array.isArray(knowledge.io) ? knowledge.io : [];

      // Filter pencarian
      if (query) {
        const ioSearch = ioList.map(i => `${i.pin} ${i.desc}`).join(' ');
        const searchTerms = `${name} ${fullName} ${k} ${cat} ${tagType} ${desc} ${how} ${ioSearch}`.toLowerCase();
        if (!searchTerms.includes(query)) {
          continue;
        }
      }

      const simVideo = knowledge.simVideo || (
        k.includes('thermal') || k.includes('overload') ? 'thermal_overload' :
        k.includes('timer_on') || k.includes('ton') || k.includes('delay_on') ? 'on_delay' :
        k.includes('timer_off') || k.includes('toff') || k.includes('delay_off') ? 'off_delay' :
        k.includes('breaker') || k.includes('mcb') || k.includes('fuse') ? 'short_circuit' :
        k.includes('horn') || k.includes('klakson') ? 'klakson_relay' :
        k.includes('flasher') || k.includes('sein') || k.includes('hazard') ? 'lampu_sein_flasher' :
        k.includes('headlamp') || k.includes('lampu_kepala') ? 'lampu_kepala_relay' :
        k.includes('starter') || k.includes('bendix') ? 'motor_starter_bendix' :
        k.includes('wiper') ? 'wiper_auto_stop' :
        k.includes('ignition') || k.includes('tci') || k.includes('pengapian') ? 'pengapian_tci' :
        k.includes('coil') || k.includes('contactor') || k.includes('motor') || k.includes('button') ? 'dol_starter' : null
      );

      let ioHtml = '';
      if (ioList.length > 0) {
        const ioItems = ioList.map(item => {
          const typeClass = (item.type || 'io').toLowerCase();
          return `
            <div class="encyclo-io-item">
              <span class="encyclo-pin-badge pin-${typeClass}">${item.pin}</span>
              <span class="encyclo-pin-desc">${item.desc}</span>
            </div>
          `;
        }).join('');
        ioHtml = `
          <div class="encyclo-io-section">
            <div class="encyclo-io-title">Jalur Terminal Input &amp; Output:</div>
            <div class="encyclo-io-grid">
              ${ioItems}
            </div>
          </div>
        `;
      }

      let iconHtml = '';
      if (v && v.graphics) {
        const vb = v.viewBox || `0 0 ${v.width || 120} ${v.height || 120}`;
        iconHtml = `<svg viewBox="${vb}" class="encyclo-icon-svg" aria-hidden="true">${v.graphics}</svg>`;
      } else {
        iconHtml = `<img src="../assets/icons/${k}.svg" alt="${name}" class="encyclo-icon-img" loading="lazy" onerror="this.style.display='none'" />`;
      }

      const card = document.createElement('div');
      card.className = 'encyclo-card';
      card.innerHTML = `
        <div class="encyclo-card-header">
          <div class="encyclo-icon-box">
            ${iconHtml}
          </div>
          <div class="encyclo-header-meta">
            <div class="encyclo-title">${name}</div>
            <div class="encyclo-badges-row">
              <span class="encyclo-tag-badge">Tag: ${tagType}</span>
              <span class="encyclo-cat-badge">${cat}</span>
            </div>
          </div>
        </div>
        <div class="encyclo-desc">
          ${fullName && fullName !== name ? `<div class="encyclo-fullname"><strong>Nama Lengkap:</strong> ${fullName}</div>` : ''}
          <div class="encyclo-explanation"><strong>Penjelasan:</strong> ${desc}</div>
        </div>
        <div class="encyclo-how"><strong>Mekanisme Kerja:</strong> ${how}</div>
        ${ioHtml}
        ${simVideo ? `<button class="btn-watch-sim" data-sim="${simVideo}">▶ Tonton Video Simulasi</button>` : ''}
      `;

      const btnSim = card.querySelector('.btn-watch-sim');
      if (btnSim) {
        btnSim.addEventListener('click', () => {
          const targetSim = btnSim.getAttribute('data-sim');
          const tabBtn = document.querySelector('.inst-tab[data-tab="tabVideoSim"]');
          if (tabBtn) tabBtn.click();
          if (window.simVideoPlayer) {
            window.simVideoPlayer.loadVideo(targetSim);
            window.simVideoPlayer.play();
          }
        });
      }

      grid.appendChild(card);
      count++;
    }

    if (count === 0) {
      grid.innerHTML = `
        <div style="text-align: center; padding: 30px; color: var(--text-muted); font-size: 13px;">
          Tidak ada komponen yang cocok dengan kata kunci "<strong>${activeEncycloQuery}</strong>".
        </div>
      `;
    }
  }

  window.renderEncyclopediaGallery = renderEncyclopediaGallery;
})();
