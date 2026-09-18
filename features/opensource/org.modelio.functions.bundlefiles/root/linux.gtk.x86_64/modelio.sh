#!/bin/bash
#######################################################################################
#       Modelio launcher
#
# Parameters available:
#  -nl en/fr                                    : Define the user interface language
#  -clean                                       : Clean the cached data.
#  -workspace workspacePath                     : Open th modelio session which using the
#                                                 "dir_path" directory as workspace data.
#  -project  projectName                        : Open directly the project_name with modelio
#                                                 is used to create the new project.
#  -batch scriptFile.py                         : Execute a Jython script
#  -param key val                               : Set the key variable with the val value.
#                                                 This variable is available in jython script.
#
#######################################################################################

main()
{
	MODELIO_PATH="$(getModelioInstallPath "$0")"
	MODELIO_WORKSPACE="${MODELIO_WORKSPACE:-${HOME}/.local/share/eclipse-mieux/workspace}"
	if [ -f "${MODELIO_PATH}/modelio.config" ] ; then
		. "${MODELIO_PATH}/modelio.config"
	fi

	UBUNTU_MENUPROXY=${UBUNTU_MENUPROXY:-0}
	LIBOVERLAY_SCROLLBAR=${LIBOVERLAY_SCROLLBAR:-0}
	SWT_WEBKIT2=${SWT_WEBKIT2:-1}
	# Use GTK3 and inherit the user's active Linux GTK theme by default.
	SWT_GTK3=${SWT_GTK3:-1}
	WEBKIT_DISABLE_COMPOSITING_MODE=${WEBKIT_DISABLE_COMPOSITING_MODE:-1}
	WEBKIT_DISABLE_DMABUF_RENDERER=${WEBKIT_DISABLE_DMABUF_RENDERER:-1}
	export UBUNTU_MENUPROXY  LIBOVERLAY_SCROLLBAR  SWT_WEBKIT2  SWT_GTK3  WEBKIT_DISABLE_COMPOSITING_MODE  WEBKIT_DISABLE_DMABUF_RENDERER

	# Use the embedded jre
	[ -x "${MODELIO_PATH}/jre/bin/java" ] && export PATH="${MODELIO_PATH}/jre/bin":${PATH}

	# Modelio's application-level workspace is distinct from Equinox's
	# instance/configuration area. Keep it aligned with Eclipse-mieux unless the
	# caller explicitly supplied another -workspace value.
	workspace_arg_present=0
	for arg in "$@"; do
		if [ "$arg" = "-workspace" ]; then
			workspace_arg_present=1
			break
		fi
	done

	if [ "$workspace_arg_present" -eq 0 ]; then
		mkdir -p "${MODELIO_WORKSPACE}"
		set -- -workspace "${MODELIO_WORKSPACE}" "$@"
	fi

	# Run modelio
	exec "${MODELIO_PATH}/modelio" "$@"
}

getRealFilePath()
{
	if [ -L "$1" ] ; then
		slnk=$(\ls -l "$1"| sed -e "s|.* -> ||")
		if [ "${slnk:0:1}" != "/" ] ; then
			filepath="$(dirname $1)/${slnk}"
		else
			filepath="${slnk}"
		fi
	else
		filepath="$1"
	fi
	echo "$(cd -P -- "$(dirname -- "${filepath}")" && printf '%s\n' "$(pwd -P)/${filepath##*/}")"
}

getModelioInstallPath()
{
	m_exec="$(getRealFilePath "$0")"
	echo "$(dirname "${m_exec}")"
}

main "$@"
