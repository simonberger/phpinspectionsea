<?php

    <error descr="[EA] Please specify classes allowed for unserialization in 2nd argument.">unserialize('...')</error>;
    <error descr="[EA] Please specify classes allowed for unserialization in 2nd argument.">unserialize('...', true)</error>;
    <error descr="[EA] Please specify classes allowed for unserialization in 2nd argument.">unserialize('...', [])</error>;

    unserialize();
    unserialize('...', false);
    unserialize('...', [stdClass::class]);