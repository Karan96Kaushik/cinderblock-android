#!/bin/sh
# Discover Android wireless-debugging devices and pair or connect to them.
#
# On the phone: Settings → Developer options → Wireless debugging.
# Pairing needs the "Pair device with pairing code" dialog open; that port
# and the 6-digit code change every time the dialog is opened. After a
# successful pair, connect uses the address shown on the main Wireless
# debugging screen (a different port).

set -eu

usage() {
    cat <<'EOF'
Usage: wireless-adb.sh [command] [host:port] [pairing-code]

Commands:
  list                 Show discovered wireless devices and current adb devices
  pair [host:port] [code]
                       Pair with a device. Prompts when arguments are omitted.
  connect [host:port]  Connect to an already paired device. Prompts if omitted.

With no command, lists devices and asks whether to pair or connect.

Examples:
  ./scripts/wireless-adb.sh
  ./scripts/wireless-adb.sh pair 192.168.1.20:37123 123456
  ./scripts/wireless-adb.sh connect 192.168.1.20:41234
EOF
}

die() {
    printf '%s\n' "$*" >&2
    exit 1
}

require_adb() {
    command -v adb >/dev/null 2>&1 || die "adb is not on PATH"
    adb start-server >/dev/null
    if ! adb mdns check >/dev/null 2>&1; then
        die "adb mDNS discovery is unavailable (adb mdns check failed)"
    fi
}

# adb mdns services
#   <instance>  _adb-tls-connect._tcp  <ip>:<port>
#   <instance>  _adb-tls-pairing._tcp  <ip>:<port>
# Discovery is racy right after the server starts, so poll briefly.
discover() {
    outfile=$1
    attempt=0
    : >"$outfile"
    while [ "$attempt" -lt 5 ]; do
        adb mdns services >"$outfile" 2>/dev/null || true
        if grep -Eq '_adb-tls-(connect|pairing)\._tcp' "$outfile"; then
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 1
    done
    return 0
}

# Print: <kind> <instance> <host:port>
# kind is "connect" or "pairing"
parse_services() {
    infile=$1
    awk '
        $2 == "_adb-tls-connect._tcp" { print "connect", $1, $3 }
        $2 == "_adb-tls-pairing._tcp" { print "pairing", $1, $3 }
    ' "$infile"
}

print_discovered() {
    services=$1
    if [ ! -s "$services" ]; then
        echo "No wireless debugging devices found."
        echo "On the phone, turn on Developer options → Wireless debugging,"
        echo "and keep the phone on the same network as this machine."
        return 1
    fi
    echo "Wireless debugging services:"
    while read -r kind instance endpoint; do
        [ -n "${kind:-}" ] || continue
        case "$kind" in
            pairing) label="pair  " ;;
            connect) label="connect" ;;
            *) label="$kind" ;;
        esac
        printf '  %-7s  %-28s  %s\n' "$label" "$instance" "$endpoint"
    done <"$services"
    return 0
}

print_connected() {
    echo "adb devices:"
    adb devices -l
}

prompt() {
    message=$1
    # Read from the terminal so this still works if stdin is a pipe.
    if [ -r /dev/tty ]; then
        printf '%s' "$message" >/dev/tty
        IFS= read -r reply </dev/tty || reply=
    else
        printf '%s' "$message"
        IFS= read -r reply || reply=
    fi
    printf '%s' "$reply"
}

choose_endpoint() {
    kind=$1
    services=$2
    matches=$3
    count=$(wc -l <"$matches" | tr -d ' ')
    if [ "$count" -eq 0 ]; then
        return 1
    fi
    if [ "$count" -eq 1 ]; then
        awk '{ print $3 }' "$matches"
        return 0
    fi
    echo "Choose a $kind target:" >&2
    n=1
    while read -r _ instance endpoint; do
        printf '  %s) %s  %s\n' "$n" "$instance" "$endpoint" >&2
        n=$((n + 1))
    done <"$matches"
    choice=$(prompt "Number (1-$count): ")
    case "$choice" in
        ''|*[!0-9]*) die "Invalid selection" ;;
    esac
    if [ "$choice" -lt 1 ] || [ "$choice" -gt "$count" ]; then
        die "Invalid selection"
    fi
    awk -v n="$choice" 'NR == n { print $3 }' "$matches"
}

filter_kind() {
    kind=$1
    services=$2
    dest=$3
    awk -v kind="$kind" '$1 == kind' "$services" >"$dest"
}

connect_endpoint() {
    endpoint=$1
    echo "Connecting to $endpoint"
    adb connect "$endpoint"
}

# After pairing, the connect port is the one on the Wireless debugging screen.
# Prefer the connect service that shares the pairing instance name, then the
# same IP.
connect_after_pair() {
    pairing_endpoint=$1
    instance=$2
    services=$3
    ip=${pairing_endpoint%%:*}

    endpoint=
    if [ -n "$instance" ]; then
        endpoint=$(awk -v name="$instance" '$1 == "connect" && $2 == name { print $3; exit }' "$services")
    fi
    if [ -z "$endpoint" ]; then
        endpoint=$(awk -v ip="$ip" '
            $1 == "connect" {
                host = $3
                sub(/:.*/, "", host)
                if (host == ip) { print $3; exit }
            }
        ' "$services")
    fi
    if [ -z "$endpoint" ]; then
        echo "Paired. No connect port was advertised yet."
        echo "Enter the IP address and port from the main Wireless debugging screen."
        manual=$(prompt "Connect host:port (empty to skip): ")
        [ -n "$manual" ] || return 0
        endpoint=$manual
    fi
    connect_endpoint "$endpoint"
}

do_pair() {
    endpoint=${1:-}
    code=${2:-}
    services=$3

    if [ -z "$endpoint" ]; then
        filter_kind pairing "$services" "$tmpdir/pairing"
        endpoint=$(choose_endpoint pairing "$services" "$tmpdir/pairing") || \
            die "No pairing services found. Open \"Pair device with pairing code\" on the phone and run this again."
    fi
    case "$endpoint" in
        *:*) ;;
        *) die "Expected host:port, got: $endpoint" ;;
    esac
    if [ -z "$code" ]; then
        code=$(prompt "Pairing code for $endpoint: ")
    fi
    case "$code" in
        [0-9][0-9][0-9][0-9][0-9][0-9]) ;;
        *) die "Pairing code must be 6 digits" ;;
    esac

    # Remember the service before pairing; the pairing advertisement
    # disappears once the dialog closes.
    instance=$(awk -v ep="$endpoint" '$1 == "pairing" && $3 == ep { print $2; exit }' "$services")

    echo "Pairing with $endpoint"
    adb pair "$endpoint" "$code"

    discover "$tmpdir/mdns-after"
    parse_services "$tmpdir/mdns-after" >"$services"
    connect_after_pair "$endpoint" "$instance" "$services"
}

do_connect() {
    endpoint=${1:-}
    services=$2
    if [ -z "$endpoint" ]; then
        filter_kind connect "$services" "$tmpdir/connect"
        endpoint=$(choose_endpoint connect "$services" "$tmpdir/connect") || \
            die "No connect services found. Pair first, or pass host:port from the Wireless debugging screen."
    fi
    case "$endpoint" in
        *:*) ;;
        *) die "Expected host:port, got: $endpoint" ;;
    esac
    connect_endpoint "$endpoint"
}

main() {
    cmd=${1:-}
    case "$cmd" in
        -h|--help|help) usage; exit 0 ;;
    esac

    require_adb

    tmpdir=$(mktemp -d)
    trap 'rm -rf "$tmpdir"' EXIT INT TERM
    discover "$tmpdir/mdns"
    parse_services "$tmpdir/mdns" >"$tmpdir/services"

    case "$cmd" in
        list)
            print_discovered "$tmpdir/services" || true
            echo
            print_connected
            ;;
        pair)
            shift
            do_pair "${1:-}" "${2:-}" "$tmpdir/services"
            echo
            print_connected
            ;;
        connect)
            shift
            do_connect "${1:-}" "$tmpdir/services"
            echo
            print_connected
            ;;
        '')
            print_discovered "$tmpdir/services" || true
            echo
            print_connected
            echo
            action=$(prompt "Action [pair/connect/quit]: ")
            case "$action" in
                pair) do_pair "" "" "$tmpdir/services" ;;
                connect) do_connect "" "$tmpdir/services" ;;
                quit|q|"") ;;
                *) die "Unknown action: $action" ;;
            esac
            ;;
        *)
            usage >&2
            die "Unknown command: $cmd"
            ;;
    esac
}

main "$@"
