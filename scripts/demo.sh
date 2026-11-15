#!/usr/bin/env bash
# 모심 시연 영상의 컷 2~12를 Virtual Console HTTP API로 재현한다. 앱 데이터를 지우고 처음 실행부터 시작한다.
# 사전: Pleos 에뮬레이터 콜드 부트, adb forward tcp:8088 tcp:8088, 앱 설치
# 사용: scripts/demo.sh                   재현(화면 버튼은 글자를 찾아 자동으로 누름)
#       SNAP=shots scripts/demo.sh        장면마다 앱 화면을 shots/NN-이름.png 로 저장
#       MOVE_MS=<ms>                      시트 앞뒤 이동 상한(기본 1000 = 가상 콘솔 50 눈금)
#       STOP_AFTER=<N>                    컷 N까지만 재현하고 끝낸다
# 에뮬레이터 시계는 cmd alarm set-time 으로 이야기 날짜(화요일 오후 2시 30분 …)에 맞추고 끝에 되돌린다.
# 에뮬레이터 대체: 벨트 = 텔테일(켜짐 = 풂), 추움 = 목표 온도 26℃, 어두움 = 전조등 로우빔, 위치 = adb emu geo fix
set -euo pipefail
SERIAL="${SERIAL:-$(adb devices | awk 'NR>1 && $2=="device"{print $1}' | head -1)}"
BASE="http://127.0.0.1:8088"
PKG=dev.gwanho.pleossample
HOME_LON=127.0276; HOME_LAT=37.4979    # 집 앞(알아본 곳). 요양병원 도착도 이 지오펜스로 보여 준다(장소 이름은 화면에 안 나옴)
FAR_LON=127.1000;  FAR_LAT=37.5500     # 10km 밖(처음 가는 식당)

adb -s "$SERIAL" forward tcp:8088 tcp:8088 >/dev/null
( while :; do curl -s -o /dev/null -X POST "$BASE/browser/session?client=demo" || true; sleep 1; done ) &   # VC 세션 핑(없으면 3초 뒤 503)
KEEPALIVE=$!
POLL=""
[ -n "${REC:-}" ] && { mkdir -p "$REC"; REC="$(cd "$REC" && pwd)"; rm -f "$REC"/cut-*.webm "$REC"/cut-*.marks "$REC"/cuts.tsv "$REC"/vc.tsv; python3 -I "$(dirname "$0")/vc-poll.py" "$BASE" "$REC/vc.tsv" & POLL=$!; }
CUT=0; CUT_T0=0
now_ms() { python3 -c 'import time; print(int(time.time()*1000))'; }
rec_stop()  { [ -n "${REC:-}" ] && [ "$CUT" != 0 ] && adb -s "$SERIAL" emu screenrecord stop >/dev/null 2>&1 || true; }
cut() { # cut <번호> <제목>: 이전 컷 녹화를 끝내고 새 토막을 시작한다
  rec_stop; [ "${STOP_AFTER:-}" = "$CUT" ] && exit 0; CUT=$1; printf '\n==== 컷 %s · %s\n' "$1" "$2"
  [ -n "${REC:-}" ] || return 0
  adb -s "$SERIAL" emu screenrecord start --time-limit 180 "$REC/cut-$1.webm" >/dev/null; CUT_T0=$(now_ms)
  printf '%s\t%s\t%s\n' "$1" "$CUT_T0" "$2" >> "$REC/cuts.tsv"
}
hold()  { rec_stop; printf '\n---- 녹화 멈춤(보여 주지 않는 구간)\n'; }   # 다음 cut 까지 녹화하지 않는다
mark()  { [ -n "${REC:-}" ] || return 0; python3 -c "import time; print(round(time.time()-$CUT_T0/1000, 2), '$1', sep='\t')" >> "$REC/cut-$CUT.marks"; }
ff()    { mark ff; }     # 여기부터 빨리 감기
ffend() { mark /ff; }    # 여기까지
label() { mark "label $1"; }   # 왼쪽 위 날짜·장소 표시(렌더 때 얹음)
restore_clock() { adb -s "$SERIAL" shell cmd alarm set-time "$(( $(date +%s) * 1000 ))" >/dev/null; }
trap 'kill $KEEPALIVE $POLL 2>/dev/null; rec_stop; restore_clock; lamp Off 2>/dev/null || true' EXIT

vc() { # vc <path> <query>: 2xx가 아니면 3번까지 다시(세션 핑을 놓친 직후 503 emulator_inactive 가 올 수 있음)
  local code i
  for i in 1 2 3; do code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$BASE$1?$2"); [ "${code:0:1}" = 2 ] && break; sleep 0.3; done
  printf '%s  PUT %s?%s -> %s\n' "$(date +%T)" "$1" "$2" "$code"
}
step()  { printf '\n== %s\n' "$*"; }
geo()   { adb -s "$SERIAL" emu geo fix "$1" "$2" >/dev/null; printf '%s  geo fix lon=%s lat=%s\n' "$(date +%T)" "$1" "$2"; }
clock() { adb -s "$SERIAL" shell cmd alarm set-time "$(( $(date -j -f "%Y-%m-%d %H:%M" "$1" +%s) * 1000 ))" >/dev/null; printf '%s  시계 → %s\n' "$(date +%T)" "$1"; }
xy_of() { # xy_of <글자> [n번째]: 화면에서 글자가 있는 노드의 중심
  adb -s "$SERIAL" shell uiautomator dump /sdcard/ui.xml >/dev/null
  adb -s "$SERIAL" shell cat /sdcard/ui.xml | python3 -I -c '
import sys, re
t = sys.argv[1]; n = int(sys.argv[2])
bs = [tuple(map(int, m.groups())) for m in re.finditer(r"text=\"" + re.escape(t) + r"\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", sys.stdin.read())]
l, tp, r, b = bs[n - 1]; print((l + r) // 2, (tp + b) // 2)' "$1" "${2:-1}"
}
tap_text() { local xy; xy=$(xy_of "$@") || { echo "tap_text: $1 없음"; return 0; }; adb -s "$SERIAL" shell input tap $xy; printf '%s  tap %s (%s)\n' "$(date +%T)" "$*" "$xy"; }
tap_xy()   { adb -s "$SERIAL" shell input tap "$1" "$2"; printf '%s  tap %s (%s %s)\n' "$(date +%T)" "$3" "$1" "$2"; }   # 앱 창은 화면 (840, 96)부터 1700×1184
hold_text() { local xy; xy=$(xy_of "$1") || { echo "hold_text: $1 없음"; return 0; }; adb -s "$SERIAL" shell input swipe $xy $xy "$2"; printf '%s  hold %s %sms\n' "$(date +%T)" "$1" "$2"; }
back()  { tap_xy 920 172 뒤로; }
snap()  { [ -n "${SNAP:-}" ] || return 0; mkdir -p "$SNAP"; adb -s "$SERIAL" exec-out screencap -p > "$SNAP/$1.png"; printf '%s  snap %s.png\n' "$(date +%T)" "$1"; }
w()     { sleep "$1"; }
tts_wait() { # tts_wait [extra]: 앱이 말하는 중이면 끝날 때까지(logcat CARE 의 마지막 TTS 줄이 「TTS idle」, 최대 25초) 기다리고 extra초 더
  local i last; w 0.8
  for i in $(seq 1 100); do
    last=$(adb -s "$SERIAL" logcat -d -s CARE:I 2>/dev/null | grep -E "TTS( idle|:)" | tail -1 || true)
    case "$last" in *"TTS idle"*|"") break;; esac; sleep 0.25
  done
  printf '%s  tts idle (+%ss)\n' "$(date +%T)" "$(( i / 4 ))"; w "${1:-0}"
}

arrive() { # 알아본 곳 지오펜스 안으로 → 「곧 도착해요」(최대 3번). FusedLocation 이 밖에 있던 것을 못 봤으면 멀리 갔다 다시 들어온다
  local i n0; n0=$(adb -s "$SERIAL" logcat -d -s CARE:I 2>/dev/null | grep -c "기억한 장소 근처" || true)
  for i in 1 2 3; do
    geo $HOME_LON $HOME_LAT; w 3
    [ "$(adb -s "$SERIAL" logcat -d -s CARE:I 2>/dev/null | grep -c "기억한 장소 근처" || true)" -gt "$n0" ] && return 0
    printf '%s  도착 안내 없음 → 멀리 갔다 다시 옴\n' "$(date +%T)"; geo $FAR_LON $FAR_LAT; w 5
  done
}
gear()     { vc /vehicle/gear "gear=$1"; }                            # Park Drive
door()     { vc /vehicle/body "door_$1=$2"; }                         # fl fr rl rr / true false
occupied() { vc /vehicle/seat "kind=occupied&area=$1&value=$2"; }     # driver passenger rear_left rear_right
belt()     { vc /vehicle/telltales/simulator "type=SeatBelt&state=$1&color=Red"; }   # On = 경고등(풂), Off = 맴
lamp()     { vc /vehicle/lights "feature=headlamp_mode&value=$1"; }   # Off LowBeam (어두움 대체. SDK registerLowBeamLightState 로 들어옴)
seat_init() { # 가운데서 출발해 양방향이 보이게. 앞뒤: 조수석 60, 뒷자리 100(가장 앞). 등받이 20(0 = 완전히 젖힘). 높이·기울기 50
  for a in rear_left rear_right; do vc /vehicle/seat "kind=position&control=slide&area=$a&value=100"; done
  vc /vehicle/seat "kind=position&control=slide&area=passenger&value=60"
  for a in passenger rear_left rear_right; do for c in recline:20 height:50 tilt:50; do vc /vehicle/seat "kind=position&control=${c%%:*}&area=$a&value=${c##*:}"; done; done
}
seat_pos() { curl -s "$BASE/vehicle/state" | python3 -I -c "import sys,json; d=json.load(sys.stdin); p=d['seat']['positions']; print('        VC:', {s: [p[k][s] for k in ('slide','recline','height','tilt')] for s in ('passenger','rear_right')}, d['body']['ambient_light']['color'], 'heat', d['hvac']['passenger'].get('seat_temp'))"; }
# 보호자 하차·복귀
son_out() { door fl true; w 0.5; occupied driver false; }
son_in()  { door fl false; w 0.5; occupied driver true; }

step "초기화: 시계 1주째 화요일, P, 운전석 착좌, 문 닫힘, 벨트 경고등(기준선), 목표 온도 18, 전조등 끔, 시트 시작 위치, 위치 = 집 앞"
clock "2026-11-03 14:25"
geo $HOME_LON $HOME_LAT
gear Park; occupied driver true; for a in passenger rear_left rear_right; do occupied $a false; done
for d in fl fr rl rr; do door $d false; done
belt On; vc /vehicle/hvac "passenger_temp=18"; lamp Off              # 목표 온도 20℃ 초과 = 추움 → 처음엔 18
seat_init; w 2; seat_pos
adb -s "$SERIAL" shell am force-stop --user 10 $PKG
adb -s "$SERIAL" shell pm clear --user 10 $PKG >/dev/null
adb -s "$SERIAL" shell pm grant --user 10 $PKG pleos.car.permission.FUSED_LOCATION 2>/dev/null || true
adb -s "$SERIAL" logcat -c || true
# 데모용 시간: 앞뒤 이동 상한 1초, 예고(「곧 움직여요」) 4.5초 = 예고 문장이 끝날 때까지, 완료(「됐어요」) 6초 = 완료 문장 뒤 2초 이상
START="am start --user 10 -n $PKG/.MainActivity --el moveLimitMs ${MOVE_MS:-1000} --el announceDelayMs 4500 --el doneMs 6000${KEEP_AUDIO:+ --ez keepAudio true}"

cut 2 "켜 두기만 하면 되고, 신호는 세 가지"        # 15초: 처음 실행 5초 → 신호 카드 세 장, 소리 세 가지(4.5초)가 끝난 뒤 3초
adb -s "$SERIAL" shell $START >/dev/null
w 4.5; snap 02-first-run
tap_xy 2367 1026 "크게 보기"; w 1.5; snap 02-cards
tap_xy 1068 1182 "소리 들려 드리기"; w 8.5                             # 「등받이를 세울게요」 → 띵 · 띵 → 딩동
tap_xy 2372 1182 닫기; w 0.5; tap_xy 2312 1182 시작하기; w 2.5; snap 02-home

cut 3 "1주째, 재기만 한다"                          # 20초: 34초 기다림과 짐 싣기만 빨리 감기
clock "2026-11-03 14:30"; label "1주째 · 11월 3일 화요일 오후 2:30 · 집 앞"
w 1; son_out; w 1.5
door fr true; ff; w 34; ffend                                        # 어머니 34초 만에 앉음
occupied passenger true; w 3; door fr false; w 3; snap 03-learned-1
ff; door fr true; w 4; door fr false; w 2; son_in; w 1.5; ffend; w 1   # 짐 싣기: 착좌 없이 닫힘 → 세지 않음

cut 4 "2주째, 한 번만 묻는다"                        # 20초+: 같은 순서는 빨리 감고, 「부모님으로 기억할까요?」(약 8초)와 답은 끝까지
clock "2026-11-10 14:30"; label "2주째 · 11월 10일 화요일 오후 2:30 · 집 앞"
ff; occupied passenger false; w 1; son_out; w 1; door fr true; w 25; ffend   # (지난주에 내리신 뒤) 같은 순서
occupied passenger true; w 2; door fr false; w 1                      # 2번째 → 「부모님이 타실 때 오래 걸렸어요 … 부모님으로 기억할까요?」
son_in; w 2; snap 04-ask; tts_wait 1                                  # 보호자가 앉으면 학습 확인 팝업. 문장이 끝난 뒤 누름
tap_text 기억하기; w 1.5; snap 04-remembered; tts_wait 1.5           # 「기억할게요. 다음부터 …」
ff; occupied passenger false; clock "2026-11-13 19:00"; label "며칠 뒤 · 처음 가는 식당 앞"; geo $FAR_LON $FAR_LAT; w 2.5; ffend
door fr true; w 2; snap 04-restaurant-wait; tts_wait 2                # 처음 가는 곳이라도 문이 열리면 그 자리를 넓힘 → 「이제 타셔도 돼요」
occupied passenger true; w 1.5; snap 04-question; tts_wait 1.5       # 빨리 앉음 + 알아본 곳·시간대 아님 → 「부모님 타셨어요?」
tap_text 아니요; w 2
hold                                                                 # 컷 5 앞: 내리고 문 닫고 집 앞으로. 넓힌 조수석이 원위치되는 것(「자리를 원래대로 돌릴게요」)은 보여 주지 않는다
occupied passenger false; door fr false; geo $HOME_LON $HOME_LAT; w 1; tts_wait 4

cut 5 "3주째, 문이 열리기 전에 맞춰 둔다"            # 30초: 홈 3초 → 기다려 주세요 → 됐어요(문장 뒤에 문 열림, 신호 설명 끝까지 초록) → 착좌·열선 4초 이상
clock "2026-11-17 14:30"; label "3주째 · 11월 17일 화요일 오후 2:30 · 집 앞 · 쌀쌀한 날"; vc /vehicle/hvac "passenger_temp=26"
w 3; son_out; w 2; snap 05-prepare-wait                              # 지난번 자리(조수석) 뒤로·등받이 → 「잠시만 기다려 주세요」, 띵
w 2.5; snap 05-prepare-done; seat_pos; tts_wait 2                    # 딩동, 「이제 타셔도 돼요. 머리 조심하세요.」 뒤 2초(초록 그대로)
door fr true; w 2; tts_wait 2                                        # 이미 초록 불. 처음 한 번 신호 설명 「'띵, 띵' 하는 동안은 …」
occupied passenger true; w 3; snap 05-boarded-heat; seat_pos; w 4   # 열선

cut 6 "다 탄 뒤, '딩동' 뒤에 출발"                    # 25초: 문 닫힘 → 곧 움직여요(예고 문장 끝까지) → 기다려 주세요 → 됐어요(문장 뒤 2초) → D 주행 화면 6초
label "3주째 · 집 앞 · 출발"
belt Off; w 1                                                        # 벨트 맴
door fr false; w 2.5; snap 06-ride-soon                              # 「등받이를 세울게요. 출발은 '딩동' 뒤에 해 주세요.」 호박색
son_in; w 3.5; snap 06-ride-wait                                     # 빨강: 등받이 → 앞뒤 복귀
w 5; snap 06-ride-done; seat_pos; tts_wait 2                         # 딩동, 「다 됐어요. 이제 출발하셔도 돼요.」 뒤 2초
gear Drive; w 2; geo $FAR_LON $FAR_LAT; w 4                           # 주행 화면. 멀리 가 둔다(컷 7에서 알아본 곳 지오펜스에 다시 들어옴)

cut 7 "요양병원 도착, 내릴 때 자세"                   # 25초: 주행 중 띠·「곧 도착해요」 끝까지 → P → 곧 움직여요 → 기다려 주세요(세 값) → 됐어요(문장 뒤 3초)
label "요양병원 정문 · 오후 2:50"
arrive; snap 07-band-arrival                                         # 지오펜스 200m 안 → 띠 + 「곧 도착해요. 내리기 편하게 자리를 바꿀게요.」
tts_wait 1.5; gear Park; w 2; snap 07-exit-soon                      # 「등받이를 세울게요. 문은 '딩동' 뒤에 열어 주세요.」
w 4; snap 07-exit-wait; w 5; snap 07-exit-done; seat_pos; tts_wait 3   # 등받이 → 높이 → 기울기 → 뒤로. 딩동, 「다 됐어요. 문을 열어 드릴 때까지 앉아 계세요.」 뒤 3초

cut 8 "두 발이 땅에 닿은 뒤, 그리고 원위치"            # 20초: 벨트 풂·「다리를 하나씩 …」 끝까지 → (빨리 감기: 하차) → 보호자 복귀 → 원위치, 돌아온 값 3초 이상
label "요양병원 정문"
belt On; w 2; tts_wait 1.5                                           # 벨트 풂 → 「다리를 하나씩 내놓고, 두 발이 땅에 닿은 뒤 일어나세요.」
ff; son_out; w 1; door fr true; w 2; occupied passenger false; w 1.5; door fr false; w 1; ffend
son_in; w 2; snap 08-restoring; w 5; snap 08-restored; seat_pos; w 3   # 보호자 복귀 → 「자리를 원래대로 돌릴게요.」, 조수석 원위치(앞뒤 → 등받이 → 높이 → 기울기)

hold                                                                 # 컷 9 앞: 주차장에 세웠다 돌아와 내리면 조수석이 먼저 준비됨(B0). 이 상태 화면은 보여 주지 않는다
clock "2026-11-17 16:30"
gear Drive; w 2; gear Park; w 1; son_out; w 3; tts_wait 7            # 조수석 준비 → 「이제 타셔도 돼요」 → 됐어요 화면(6초)이 닫힌 뒤
cut 9 "치료 뒤, 다른 문이 열려도"                     # 15초: 조수석이 준비된 홈 2초 → 2열 오른쪽 문 → 기다려 주세요 · 2열 오른쪽 → 됐어요(문장 뒤 착좌) → 값 2초 이상
label "치료 뒤 · 요양병원 정문 · 오후 4:30"
w 2; door rr true; w 1.5; snap 09-rear-wait                          # 열린 문 기준으로 그 자리를 넓힘. 조수석은 그대로
w 3; snap 09-rear-done; seat_pos; tts_wait 1.5                       # 딩동, 「이제 타셔도 돼요. 머리 조심하세요.」
occupied rear_right true; w 3.5

cut 10 "'딩동' 전에 출발하면"                        # 15초: 문 닫힘·운전석 착좌 → 곧 움직여요(예고 문장 끝까지) → 기다려 주세요·띵 → 등받이 세우는 중 D → 띠 5초 이상
label "요양병원 정문 · 뒤차가 기다림"
belt Off; w 0.5
door rr false; w 0.3; son_in                                         # 「등받이를 세울게요. 출발은 '딩동' 뒤에 해 주세요.」(4.5초 예고). 안 쓴 조수석은 원위치(「자리를 원래대로 돌릴게요」는 그 뒤에 이어 말함)
w 1.5; snap 10-ride-soon; w 2.7; snap 10-ride-wait                   # 예고 뒤 등받이 세우기 시작(펄스 0.4초 켬·0.6초 쉼)
w 0.8; gear Drive; w 2; snap 10-band-stopped; seat_pos; w 5          # 등받이를 세우는 중(두 번째 펄스 뒤 쉬는 0.6초) D → 그 자리에서 멈춤, 띠 「출발해서 자리 맞추기를 멈췄어요」. 펄스 켜진 중이면 STOP 이 거부돼 끝까지 간다

hold                                                                 # 컷 11 앞: 해 진 집 앞 P. 끊겼던 등받이를 이어서 세우고 내릴 때 자세까지(예고 → 기다려 주세요 → 됐어요). 보여 주지 않는다
clock "2026-11-17 18:40"; lamp LowBeam
gear Park; w 11; tts_wait 7                                          # 예고 4.5초 → 등받이·높이·기울기 약 5초 → 「다 됐어요 …」(약 10초 뒤 시작)가 끝나고 됐어요 화면(6초)이 닫힌 뒤
cut 11 "해 진 집 앞, 독서등과 원위치"                 # 20초: 운전석 하차 → 2열 오른쪽 문 → 독서등 4초 이상 → (빨리 감기: 하차) → 보호자 복귀 → 원위치
label "집 앞 · 오후 6:40 · 해 진 뒤"
w 1; son_out; w 1.5; door rr true; w 2.5; snap 11-rear-light; w 3  # 어두움(로우빔) → 그 자리 독서등
ff; occupied rear_right false; w 1; door rr false; w 1; ffend
son_in; w 2.5; snap 11-restoring; w 5; snap 11-restored; seat_pos; w 3   # 「자리를 원래대로 돌릴게요.」 2열 오른쪽 원위치(조수석은 컷 10에서 이미 돌아옴)

cut 12 "보호자가 보는 것"                            # 15초: 이동 변화 7초 → 자세 맞추기 → 이대로 기억 → 저장된 뒤 홈 3초 이상
clock "2027-01-19 15:10"; label "두 달 뒤 · 1월 19일 화요일 · 어머니를 내려 드린 뒤"; lamp Off
SEED=$(python3 -c '
import datetime, calendar
rows = []
for (y, m, ex) in ((2026, 12, 20), (2027, 1, 28)):
    for d in range(1, 19, 2):
        t = int(calendar.timegm(datetime.datetime(y, m, d, 5, 30).timetuple()) * 1000)   # 14:30 KST
        rows += [f"{t},PASSENGER,31,true,1,BOARD", f"{t + 1200000},PASSENGER,{ex},false,1,EXIT"]
print("|".join(rows))')
adb -s "$SERIAL" shell am force-stop --user 10 $PKG
adb -s "$SERIAL" shell $START --es seedRides "'$SEED'" >/dev/null
w 4; snap 12-mobility; w 3.5                                         # 「지난달보다 내리실 때 평균 8초 더 걸렸어요」
tap_xy 2367 1182 확인; w 1.5
adb -s "$SERIAL" shell input swipe 1405 768 1405 768 900; w 3; snap 12-posture   # 홈 조수석 칸 길게 누름 → 자세 맞추기
tap_xy 2362 172 "이대로 기억"; w 1.5; back; w 3.5; snap 12-home-end

rec_stop
step "끝"
