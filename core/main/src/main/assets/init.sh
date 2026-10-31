#!/bin/sh
# Note: do NOT use `set -e` here. Several operations below (apk update, apk add,
# identity file checks) are allowed to fail without killing the terminal —
# e.g. when the device has no network. We handle each failure explicitly.

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin
export HOME=/root
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
export PIP_BREAK_SYSTEM_PACKAGES=1

mkdir -p "$HOME"

if [ ! -s /etc/resolv.conf ]; then
    echo "nameserver 8.8.8.8" > /etc/resolv.conf
    echo "nameserver 8.8.4.4" >> /etc/resolv.conf
fi

sanitize_name() {
    cleaned=$(printf "%s" "$1" | tr -cd 'A-Za-z0-9_-' | cut -c1-32)
    [ -n "$cleaned" ] && printf "%s" "$cleaned"
}

write_identity_files() {
    user_name=$(sanitize_name "$1")
    host_name=$(sanitize_name "$2")
    [ -z "$user_name" ] && user_name="user"
    [ -z "$host_name" ] && host_name="andlinux"

    cat > "$HOME/.andlinux_identity" <<EOF
ANDLINUX_USER='$user_name'
ANDLINUX_HOST='$host_name'
EOF

    echo "$host_name" > /etc/hostname
    cat > /etc/hosts <<EOF
127.0.0.1 localhost $host_name
::1 localhost $host_name
EOF

    mkdir -p /etc/profile.d
    cat > /etc/profile.d/andlinux-identity.sh <<EOF
export ANDLINUX_USER='$user_name'
export ANDLINUX_HOST='$host_name'
export USER='$user_name'
export LOGNAME='$user_name'
export HOSTNAME='$host_name'
case "\$-" in
  *i*) PS1='$user_name@$host_name:\\w# ' ;;
esac
EOF

    cat > "$HOME/.bash_profile" <<EOF
[ -f /etc/profile ] && . /etc/profile
[ -f ~/.bashrc ] && . ~/.bashrc
EOF

    cat > "$HOME/.zprofile" <<EOF
export ANDLINUX_USER='$user_name'
export ANDLINUX_HOST='$host_name'
export USER='$user_name'
export LOGNAME='$user_name'
export HOSTNAME='$host_name'
PROMPT='$user_name@$host_name:%~# '
EOF

    mkdir -p "$HOME/.config/fish"
    cat > "$HOME/.config/fish/config.fish" <<EOF
set -gx ANDLINUX_USER '$user_name'
set -gx ANDLINUX_HOST '$host_name'
set -gx USER '$user_name'
set -gx LOGNAME '$user_name'
set -gx HOSTNAME '$host_name'
function fish_prompt
    set_color cyan
    printf '$user_name'
    set_color normal
    printf '@'
    set_color green
    printf '$host_name'
    set_color normal
    printf ':'
    set_color blue
    prompt_pwd
    set_color normal
    printf '# '
end
EOF
}

install_identity_tool() {
    cat > /usr/local/bin/andlinux-identity <<'EOF'
#!/bin/sh
sanitize_name() {
    cleaned=$(printf "%s" "$1" | tr -cd 'A-Za-z0-9_-' | cut -c1-32)
    [ -n "$cleaned" ] && printf "%s" "$cleaned"
}

write_identity_files() {
    user_name=$(sanitize_name "$1")
    host_name=$(sanitize_name "$2")
    [ -z "$user_name" ] && user_name="user"
    [ -z "$host_name" ] && host_name="andlinux"

    cat > "$HOME/.andlinux_identity" <<EOT
ANDLINUX_USER='$user_name'
ANDLINUX_HOST='$host_name'
EOT
    echo "$host_name" > /etc/hostname
    cat > /etc/hosts <<EOT
127.0.0.1 localhost $host_name
::1 localhost $host_name
EOT
    mkdir -p /etc/profile.d
    cat > /etc/profile.d/andlinux-identity.sh <<EOT
export ANDLINUX_USER='$user_name'
export ANDLINUX_HOST='$host_name'
export USER='$user_name'
export LOGNAME='$user_name'
export HOSTNAME='$host_name'
case "\$-" in
  *i*) PS1='$user_name@$host_name:\\w# ' ;;
esac
EOT
    cat > "$HOME/.bash_profile" <<EOT
[ -f /etc/profile ] && . /etc/profile
[ -f ~/.bashrc ] && . ~/.bashrc
EOT
    cat > "$HOME/.zprofile" <<EOT
export ANDLINUX_USER='$user_name'
export ANDLINUX_HOST='$host_name'
export USER='$user_name'
export LOGNAME='$user_name'
export HOSTNAME='$host_name'
PROMPT='$user_name@$host_name:%~# '
EOT
    mkdir -p "$HOME/.config/fish"
    cat > "$HOME/.config/fish/config.fish" <<EOT
set -gx ANDLINUX_USER '$user_name'
set -gx ANDLINUX_HOST '$host_name'
set -gx USER '$user_name'
set -gx LOGNAME '$user_name'
set -gx HOSTNAME '$host_name'
function fish_prompt
    set_color cyan
    printf '$user_name'
    set_color normal
    printf '@'
    set_color green
    printf '$host_name'
    set_color normal
    printf ':'
    set_color blue
    prompt_pwd
    set_color normal
    printf '# '
end
EOT
}

printf 'AndLinux identity setup\n'
printf 'User name [user]: '
read -r user_name
printf 'Host name [andlinux]: '
read -r host_name
[ -z "$user_name" ] && user_name=user
[ -z "$host_name" ] && host_name=andlinux
write_identity_files "$user_name" "$host_name"
printf 'Saved. Open a new session or run: exec $SHELL -l\n'
EOF
    chmod 755 /usr/local/bin/andlinux-identity
}

first_identity_setup() {
    install_identity_tool
    if [ ! -f "$HOME/.andlinux_identity" ]; then
        write_identity_files "user" "andlinux"
    else
        . "$HOME/.andlinux_identity"
        write_identity_files "$ANDLINUX_USER" "$ANDLINUX_HOST"
    fi
}

install_packages() {
    missing=""
    for pkg in "$@"; do
        if ! apk info -e "$pkg" >/dev/null 2>&1; then
            missing="$missing $pkg"
        fi
    done

    if [ -n "$missing" ]; then
        echo -e "\e[34;1m[*]\e[0m Installing packages:$missing"
        # apk update may fail (no network). Don't let that kill the script —
        # just try to install the packages directly; if that also fails we
        # print a clear error and continue so the user still gets a shell.
        apk update 2>&1 || echo -e "\e[33;1m[!]\e[0m apk update failed (no network?), continuing"
        if apk add $missing 2>&1; then
            echo -e "\e[32;1m[+]\e[0m Packages installed"
        else
            echo -e "\e[33;1m[!]\e[0m Failed to install:$missing — continuing without them"
            return 1
        fi
    fi
    return 0
}

# Base compatibility packages. Keep this list small; optional shells are installed only when selected.
install_packages bash gcompat glib nano
first_identity_setup

# OpenCode is installed on startup so the web chat interface is available.
setup_opencode() {
    if [ ! -x /usr/bin/opencode ] && [ ! -x /usr/local/bin/opencode ]; then
        echo -e "\e[34;1m[*]\e[0m Installing OpenCode dependencies (nodejs, npm, @opencode/cli)..."
        if apk add --no-cache nodejs npm && npm install -g @opencode/cli; then
            echo -e "\e[32;1m[+]\e[0m OpenCode installed successfully"
        else
            echo -e "\e[33;1m[!]\e[0m OpenCode installation failed; retry from the terminal with: npm install -g @opencode/cli"
            return 1
        fi
    fi

    if command -v opencode >/dev/null 2>&1; then
        for pid in $(ps | awk '/[o]pencode/ {print $1}'); do
            kill "$pid" 2>/dev/null || true
        done
        sleep 1
    fi
    if command -v opencode >/dev/null 2>&1 && ! ps | grep -q '[o]pencode.*4123'; then
        echo -e "\e[34;1m[*]\e[0m Starting OpenCode web server on port 4123..."
        OPENCODE_SERVER_PASSWORD='andlinux-local-opencode' \
            nohup opencode web --port 4123 --hostname 127.0.0.1 >/tmp/opencode.log 2>&1 &
        sleep 2
        if ! ps | grep -q '[o]pencode.*4123'; then
            OPENCODE_SERVER_PASSWORD='andlinux-local-opencode' \
                nohup opencode serve --port 4123 --hostname 127.0.0.1 >/tmp/opencode.log 2>&1 &
        fi
        sleep 1
        touch /tmp/opencode_ready 2>/dev/null || true
    fi
}

setup_opencode || true

# Fix Android linker warning in some proot builds.
if [ ! -f /linkerconfig/ld.config.txt ]; then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi

select_shell() {
    case "${ANDLINUX_DEFAULT_SHELL:-ash}" in
        ash|sh)
            SELECTED_SHELL=/bin/ash
            SELECTED_PKG=""
            ;;
        bash)
            SELECTED_SHELL=/bin/bash
            SELECTED_PKG=bash
            ;;
        fish)
            SELECTED_SHELL=/usr/bin/fish
            SELECTED_PKG=fish
            ;;
        zsh)
            SELECTED_SHELL=/bin/zsh
            SELECTED_PKG=zsh
            ;;
        *)
            echo "Unknown shell '${ANDLINUX_DEFAULT_SHELL}', falling back to ash"
            SELECTED_SHELL=/bin/ash
            SELECTED_PKG=""
            ;;
    esac
}

select_shell

if [ -n "$SELECTED_PKG" ] && [ ! -x "$SELECTED_SHELL" ]; then
    if ! install_packages "$SELECTED_PKG"; then
        echo "Failed to install shell package '$SELECTED_PKG', falling back to ash"
        SELECTED_SHELL=/bin/ash
    fi
fi

if [ ! -x "$SELECTED_SHELL" ]; then
    echo "Shell '$SELECTED_SHELL' is not executable, falling back to ash"
    SELECTED_SHELL=/bin/ash
fi

export SHELL="$SELECTED_SHELL"
cd "$HOME"

if [ "$#" -eq 0 ]; then
    exec "$SELECTED_SHELL" -l
else
    exec "$@"
fi
